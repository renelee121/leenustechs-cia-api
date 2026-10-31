package mx.com.leenustechs.ciaApi.business.services;

import java.util.UUID;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.IdempotencyException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.InvalidPayloadException;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private static final String OPERATION = "LOGIN";
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommonModelResponse executeLogin(String key, JsonNode payload,
            Function<String, CommonModelResponse> publish) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IdempotencyException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key is required (1-128 ASCII letters, digits, ., _, :, -)", null);
        }
        if (payload == null || !payload.isObject()) {
            throw new InvalidPayloadException();
        }

        String transactionId = UUID.randomUUID().toString();
        // The unique constraint claims the key before any Kafka side effect.
        // Keep this insert committed independently: never wrap publication in a DB transaction.
        try {
            jdbc.update("""
                    INSERT INTO http_idempotency
                    (operation, idempotency_key, transaction_id, payload, state)
                    VALUES (?, ?, ?, ?, 'IN_PROGRESS')
                    """, OPERATION, key, transactionId, mapper.writeValueAsString(payload));
        } catch (DuplicateKeyException duplicate) {
            return replay(key, payload);
        }

        try {
            CommonModelResponse response = publish.apply(transactionId);
            jdbc.update("""
                    UPDATE http_idempotency SET state = 'COMPLETED', response = ?
                    WHERE operation = ? AND idempotency_key = ?
                    """, mapper.writeValueAsString(response), OPERATION, key);
            return response;
        } catch (RuntimeException failure) {
            // Even a timeout may have delivered the event. Never release this key for republishing.
            try {
                jdbc.update("""
                        UPDATE http_idempotency SET state = 'UNCONFIRMED'
                        WHERE operation = ? AND idempotency_key = ? AND state = 'IN_PROGRESS'
                        """, OPERATION, key);
            } catch (RuntimeException persistenceFailure) {
                failure.addSuppressed(persistenceFailure);
                // The committed IN_PROGRESS row still blocks duplicate publication.
            }
            throw failure;
        }
    }

    private CommonModelResponse replay(String key, JsonNode payload) {
        return jdbc.queryForObject("""
                SELECT transaction_id, payload, state, response FROM http_idempotency
                WHERE operation = ? AND idempotency_key = ?
                """, (row, index) -> {
            String transactionId = row.getString("transaction_id");
            if (!mapper.readTree(row.getString("payload")).equals(payload)) {
                throw new IdempotencyException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "Idempotency-Key was already used with a different payload", transactionId);
            }
            return switch (row.getString("state")) {
                case "COMPLETED" -> mapper.readValue(row.getString("response"), CommonModelResponse.class);
                case "UNCONFIRMED" -> throw new IdempotencyException(HttpStatus.SERVICE_UNAVAILABLE,
                        "KAFKA_PUBLICATION_UNCONFIRMED",
                        "Kafka publication could not be confirmed; reconciliation is required", transactionId);
                default -> throw new IdempotencyException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                        "Publication is in progress or requires reconciliation after an interruption",
                        transactionId);
            };
        }, OPERATION, key);
    }
}

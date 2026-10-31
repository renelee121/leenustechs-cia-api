package mx.com.leenustechs.ciaApi.business.services;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.IdempotencyException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.InvalidPayloadException;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private static final String PREFIX = "cia-api:idempotency:login:";
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then return current end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', 30000)
            return ''
            """, String.class);
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final JsonMapper mapper;

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
        String redisKey = PREFIX + key;
        String pending = entry(transactionId, payload, "IN_PROGRESS", null);
        String existing;
        try {
            existing = redis.execute(CLAIM, List.of(redisKey), pending);
        } catch (RuntimeException failure) {
            throw unavailable(transactionId);
        }
        if (existing == null) {
            throw unavailable(transactionId);
        }
        if (!existing.isEmpty()) {
            return replay(existing, payload);
        }

        try {
            CommonModelResponse response = publish.apply(transactionId);
            save(redisKey, pending, entry(transactionId, payload, "COMPLETED", response), transactionId);
            return response;
        } catch (RuntimeException failure) {
            try {
                save(redisKey, pending, entry(transactionId, payload, "UNCONFIRMED", null), transactionId);
            } catch (RuntimeException cacheFailure) {
                failure.addSuppressed(cacheFailure);
            }
            throw failure;
        }
    }

    private String entry(String transactionId, JsonNode payload, String state, CommonModelResponse response) {
        var node = mapper.createObjectNode().put("transactionId", transactionId).put("state", state);
        node.set("payload", payload);
        if (response != null) {
            node.set("response", mapper.valueToTree(response));
        }
        return mapper.writeValueAsString(node);
    }

    private void save(String key, String pending, String result, String transactionId) {
        try {
            // Match the entire original claim, including its unique owner.
            // Expired claims must not recreate keys or overwrite a newer request.
            // KEEPTTL preserves the original deadline; retries never extend it.
            redis.execute(COMPLETE, List.of(key), pending, result);
        } catch (RuntimeException failure) {
            throw unavailable(transactionId);
        }
    }

    private CommonModelResponse replay(String stored, JsonNode payload) {
        var entry = mapper.readTree(stored);
        String transactionId = entry.get("transactionId").asString();
        if (!entry.get("payload").equals(payload)) {
            throw new IdempotencyException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency-Key was already used with a different payload", transactionId);
        }
        return switch (entry.get("state").asString()) {
            case "COMPLETED" -> mapper.treeToValue(entry.get("response"), CommonModelResponse.class);
            case "UNCONFIRMED" -> throw new IdempotencyException(HttpStatus.SERVICE_UNAVAILABLE,
                    "KAFKA_PUBLICATION_UNCONFIRMED",
                    "Kafka publication could not be confirmed within the idempotency window", transactionId);
            default -> throw new IdempotencyException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                    "Publication is in progress within the idempotency window", transactionId);
        };
    }

    private IdempotencyException unavailable(String transactionId) {
        return new IdempotencyException(HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_STORE_UNAVAILABLE",
                "Redis idempotency store is unavailable", transactionId);
    }
}

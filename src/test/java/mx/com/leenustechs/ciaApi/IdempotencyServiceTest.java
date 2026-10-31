package mx.com.leenustechs.ciaApi;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import mx.com.leenustechs.ciaApi.business.services.IdempotencyService;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.IdempotencyException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.KafkaPublicationException;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    private DataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private IdempotencyService service(DataSource source) {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        return new IdempotencyService(new JdbcTemplate(source), mapper);
    }

    private CommonModelResponse response(String transactionId) {
        return new CommonModelResponse(transactionId, "CIA_API", OperationType.LOGIN,
                mapper.createObjectNode().put("status", "success"));
    }

    @Test
    void completedRequestReplaysAcrossIndependentServiceInstances() {
        var source = source();
        var first = service(source);
        var second = service(source);
        var count = new AtomicInteger();
        var result = first.executeLogin("key", mapper.readTree("{\"a\":1,\"b\":2}"), id -> {
            count.incrementAndGet();
            return response(id);
        });
        var replay = second.executeLogin("key", mapper.readTree("{\"b\":2,\"a\":1}"), id -> {
            fail("Must not publish twice");
            return null;
        });
        assertEquals(result, replay);
        assertEquals(1, count.get());
    }

    @Test
    void differentPayloadConflictsEvenAfterSuccess() {
        var service = service(source());
        service.executeLogin("key", mapper.createObjectNode().put("user", "one"), this::response);
        var conflict = assertThrows(IdempotencyException.class,
                () -> service.executeLogin("key", mapper.createObjectNode().put("user", "two"), id -> {
                    fail("Conflicting key must not publish");
                    return null;
                }));
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", conflict.getCode());
        assertEquals(409, conflict.getStatus().value());
    }

    @Test
    void concurrentRequestCannotPublishWhileFirstIsPending() throws Exception {
        var source = source();
        var first = service(source);
        var second = service(source);
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> first.executeLogin("key", mapper.createObjectNode(), id -> {
                claimed.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return response(id);
            }));
            try {
                assertTrue(claimed.await(5, TimeUnit.SECONDS));
                var conflict = assertThrows(IdempotencyException.class,
                        () -> second.executeLogin("key", mapper.createObjectNode(), id -> {
                            fail("Concurrent request must not publish");
                            return null;
                        }));
                assertEquals("IDEMPOTENCY_IN_PROGRESS", conflict.getCode());
            } finally {
                release.countDown();
            }
            var result = pending.get(5, TimeUnit.SECONDS);
            assertEquals(result, second.executeLogin("key", mapper.createObjectNode(), id -> {
                fail("Completed concurrent request must replay");
                return null;
            }));
        }
    }

    @Test
    void uncertainPublicationNeverRepublishesOnRetry() {
        var source = source();
        var first = service(source);
        assertThrows(KafkaPublicationException.class,
                () -> first.executeLogin("key", mapper.createObjectNode(), id -> {
                    throw new KafkaPublicationException(id, new TimeoutException());
                }));
        var second = service(source);
        var failure = assertThrows(IdempotencyException.class,
                () -> second.executeLogin("key", mapper.createObjectNode(), id -> {
                    fail("Uncertain delivery must not republish");
                    return null;
                }));
        assertEquals("KAFKA_PUBLICATION_UNCONFIRMED", failure.getCode());
        assertEquals(503, failure.getStatus().value());
    }

    @Test
    void durableLedgerSurvivesClosingAndReopeningDatabase(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("ledger").toAbsolutePath();
        var first = service(new DriverManagerDataSource(url, "sa", ""));
        var response = first.executeLogin("key", mapper.createObjectNode(), this::response);
        var restarted = service(new DriverManagerDataSource(url, "sa", ""));
        assertEquals(response, restarted.executeLogin("key", mapper.createObjectNode(), id -> {
            fail("Restart must not lose completed key");
            return null;
        }));
    }

    @Test
    void abandonedClaimIsNotReclaimedAfterRestart() {
        var source = source();
        var service = service(source);
        new JdbcTemplate(source).update("""
                INSERT INTO http_idempotency
                (operation, idempotency_key, transaction_id, payload, state)
                VALUES ('LOGIN', 'key', 'old-transaction', '{}', 'IN_PROGRESS')
                """);
        var exception = assertThrows(IdempotencyException.class,
                () -> service.executeLogin("key", mapper.createObjectNode(), id -> {
                    fail("A crash might have happened after sending to Kafka");
                    return null;
                }));
        assertEquals("IDEMPOTENCY_IN_PROGRESS", exception.getCode());
    }

    @Test
    void invalidKeysAreRejectedBeforePublication() {
        var service = service(source());
        for (String key : new String[] {null, "", " ", "a".repeat(129), "bad\nkey"}) {
            assertEquals("INVALID_IDEMPOTENCY_KEY", assertThrows(IdempotencyException.class,
                    () -> service.executeLogin(key, mapper.createObjectNode(), id -> {
                        fail("Invalid key must not publish");
                        return null;
                    })).getCode());
        }
    }

    @Test
    void persistenceFailurePreventsPublication() {
        var source = source();
        var service = service(source);
        new JdbcTemplate(source).execute("DROP TABLE http_idempotency");
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.executeLogin("key", mapper.createObjectNode(), id -> {
                    fail("Unavailable ledger must fail closed");
                    return null;
                }));
    }
}

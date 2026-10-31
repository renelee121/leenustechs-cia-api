package mx.com.leenustechs.ciaApi;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import mx.com.leenustechs.ciaApi.business.services.IdempotencyService;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.IdempotencyException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.KafkaPublicationException;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {
    private static final RedisTestServer server = RedisTestServer.start();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @AfterAll
    static void stopRedis() { server.close(); }

    private IdempotencyService service() {
        return new IdempotencyService(server.redis, mapper);
    }

    private String redisKey(String key) { return "cia-api:idempotency:login:" + key; }

    private CommonModelResponse response(String transactionId) {
        return new CommonModelResponse(transactionId, "CIA_API", OperationType.LOGIN,
                mapper.createObjectNode().put("status", "success"));
    }

    @Test
    void completedRequestReplaysAcrossIndependentServiceInstances() {
        String key = UUID.randomUUID().toString();
        var count = new AtomicInteger();
        var result = service().executeLogin(key, mapper.readTree("{\"a\":1,\"b\":2}"), id -> {
            count.incrementAndGet();
            return response(id);
        });
        var replay = service().executeLogin(key, mapper.readTree("{\"b\":2,\"a\":1}"), id -> {
            fail("Must not publish twice");
            return null;
        });
        assertEquals(result, replay);
        assertEquals(1, count.get());
    }

    @Test
    void differentPayloadConflictsEvenAfterSuccess() {
        String key = UUID.randomUUID().toString();
        service().executeLogin(key, mapper.createObjectNode().put("user", "one"), this::response);
        var conflict = assertThrows(IdempotencyException.class,
                () -> service().executeLogin(key, mapper.createObjectNode().put("user", "two"), id -> {
                    fail("Conflicting key must not publish");
                    return null;
                }));
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", conflict.getCode());
        assertEquals(409, conflict.getStatus().value());
    }

    @Test
    void concurrentRequestsClaimKeyAtomically() throws Exception {
        String key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = new java.util.ArrayList<Future<Object>>();
            for (int i = 0; i < 2; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    try {
                        return service().executeLogin(key, mapper.createObjectNode(), id -> {
                            count.incrementAndGet();
                            entered.countDown();
                            try {
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            return response(id);
                        });
                    } catch (IdempotencyException conflict) {
                        return conflict;
                    }
                }));
            }
            start.countDown();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (calls.stream().noneMatch(Future::isDone) && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertTrue(calls.stream().anyMatch(Future::isDone));
            } finally {
                release.countDown();
            }
            var results = new java.util.ArrayList<Object>();
            for (var call : calls) results.add(call.get(5, TimeUnit.SECONDS));
            assertEquals(1, count.get());
            assertEquals(1, results.stream().filter(CommonModelResponse.class::isInstance).count());
            var conflict = (IdempotencyException) results.stream().filter(IdempotencyException.class::isInstance)
                    .findFirst().orElseThrow();
            assertEquals("IDEMPOTENCY_IN_PROGRESS", conflict.getCode());
        }
    }

    @Test
    void uncertainPublicationDoesNotRepublishWithinWindow() {
        String key = UUID.randomUUID().toString();
        assertThrows(KafkaPublicationException.class,
                () -> service().executeLogin(key, mapper.createObjectNode(), id -> {
                    throw new KafkaPublicationException(id, new TimeoutException());
                }));
        var failure = assertThrows(IdempotencyException.class,
                () -> service().executeLogin(key, mapper.createObjectNode(), id -> {
                    fail("Uncertain delivery must not republish within TTL");
                    return null;
                }));
        assertEquals("KAFKA_PUBLICATION_UNCONFIRMED", failure.getCode());
        assertEquals(503, failure.getStatus().value());
        assertTtl(key);
    }

    @Test
    void claimHasSixtySecondTtlAndCompletionDoesNotRenewIt() {
        String key = UUID.randomUUID().toString();
        service().executeLogin(key, mapper.createObjectNode(), id -> {
            long initialTtl = server.redis.getExpire(redisKey(key), TimeUnit.MILLISECONDS);
            assertTrue(initialTtl > 59000 && initialTtl <= 60000,
                    "A new claim must start with a 60-second TTL");
            // Simulate time consumed by Kafka without making the suite wait 55 seconds.
            server.redis.expire(redisKey(key), Duration.ofSeconds(5));
            return response(id);
        });
        long ttl = server.redis.getExpire(redisKey(key), TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 5000, "Completion must preserve remaining TTL");
        service().executeLogin(key, mapper.createObjectNode(), id -> {
            fail("Replay must not publish");
            return null;
        });
        assertTrue(server.redis.getExpire(redisKey(key), TimeUnit.MILLISECONDS) <= ttl,
                "Replay must not renew TTL");
    }

    @Test
    void expirationAllowsNewPublicationAndOldCompletionCannotOverwriteIt() throws Exception {
        String key = UUID.randomUUID().toString();
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var old = executor.submit(() -> service().executeLogin(key, mapper.createObjectNode(), id -> {
                claimed.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return response(id);
            }));
            CommonModelResponse replacement;
            try {
                assertTrue(claimed.await(5, TimeUnit.SECONDS));
                // Accelerate actual Redis expiry for the boundary/race test.
                server.redis.expire(redisKey(key), Duration.ofMillis(1));
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (Boolean.TRUE.equals(server.redis.hasKey(redisKey(key))) && System.nanoTime() < deadline) {
                    Thread.sleep(5);
                }
                assertFalse(server.redis.hasKey(redisKey(key)));
                replacement = service().executeLogin(key, mapper.createObjectNode(), this::response);
            } finally {
                release.countDown();
            }
            assertNotEquals(old.get(5, TimeUnit.SECONDS).getTransactionId(), replacement.getTransactionId());
            assertEquals(replacement, service().executeLogin(key, mapper.createObjectNode(), id -> {
                fail("Old owner must not replace the new cached response");
                return null;
            }));
            assertTtl(key);
        }
    }

    @Test
    void expiredCompletionDoesNotRecreateKey() {
        String key = UUID.randomUUID().toString();
        service().executeLogin(key, mapper.createObjectNode(), id -> {
            server.redis.delete(redisKey(key));
            return response(id);
        });
        assertFalse(server.redis.hasKey(redisKey(key)));
    }

    @Test
    void invalidKeysAreRejectedBeforePublication() {
        for (String key : new String[] {null, "", " ", "a".repeat(129), "bad\nkey"}) {
            assertEquals("INVALID_IDEMPOTENCY_KEY", assertThrows(IdempotencyException.class,
                    () -> service().executeLogin(key, mapper.createObjectNode(), id -> {
                        fail("Invalid key must not publish");
                        return null;
                    })).getCode());
        }
    }

    @Test
    void unavailableRedisPreventsPublication() {
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(Object[].class))).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
        var service = new IdempotencyService(redis, mapper);
        var error = assertThrows(IdempotencyException.class,
                () -> service.executeLogin("key", mapper.createObjectNode(), id -> {
                    fail("Unavailable Redis must fail closed");
                    return null;
                }));
        assertEquals("IDEMPOTENCY_STORE_UNAVAILABLE", error.getCode());
        assertEquals(503, error.getStatus().value());
    }

    private void assertTtl(String key) {
        long ttl = server.redis.getExpire(redisKey(key), TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 60000, "Every state must expire within 60 seconds");
    }
}

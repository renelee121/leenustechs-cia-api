package mx.com.leenustechs.ciaApi;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import mx.com.leenustechs.ciaApi.business.services.IdempotencyService;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.IdempotencyException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.KafkaPublicationException;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {
    private final MockRedisScripts store = new MockRedisScripts();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private IdempotencyService service() {
        return new IdempotencyService(store.redis, mapper);
    }

    private String redisKey(String key) {
        return "cia-api:idempotency:login:" + key;
    }

    private CommonModelResponse response(String transactionId) {
        return new CommonModelResponse(transactionId, "CIA_API", OperationType.LOGIN,
                mapper.createObjectNode().put("status", "success"));
    }

    @Test
    void usesInterceptorTransactionIdForPublicationAndCachedRecord() {
        String key = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        var result = service().executeLogin(key, id, mapper.createObjectNode().put("user", "alice"),
                received -> {
                    assertEquals(id, received);
                    return response(received);
                });
        assertEquals(id, result.getTransactionId());
        assertEquals(id, mapper.readTree(store.get(redisKey(key))).get("transactionId").asString());
    }

    @Test
    void rejectsMissingInterceptorTransactionId() {
        assertThrows(IllegalStateException.class, () ->
                service().executeLogin("key", null, mapper.createObjectNode(), this::response));
        verifyNoInteractions(store.redis);
    }

    @Test
    void replaysCompletedRequestWithoutCallingPublisherAgain() {
        String key = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        var calls = new AtomicInteger();
        var first = service().executeLogin(key, id, mapper.readTree("{\"a\":1,\"b\":2}"),
                transaction -> {
                    calls.incrementAndGet();
                    return response(transaction);
                });
        var second = new IdempotencyService(store.redis, mapper)
                .executeLogin(key, UUID.randomUUID().toString(), mapper.readTree("{\"b\":2,\"a\":1}"),
                        transaction -> {
                            fail("Replay must never publish");
                            return null;
                        });
        assertEquals(first, second);
        assertEquals(id, second.getTransactionId());
        assertEquals(1, calls.get());
    }

    @Test
    void conflictingPayloadIsRejected() {
        String key = UUID.randomUUID().toString();
        service().executeLogin(key, UUID.randomUUID().toString(),
                mapper.createObjectNode().put("user", "one"), this::response);
        var ex = assertThrows(IdempotencyException.class, () -> service().executeLogin(
                key, UUID.randomUUID().toString(),
                mapper.createObjectNode().put("user", "two"), this::response));
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void concurrentClaimsOnlyInvokePublisherOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var publications = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = new java.util.ArrayList<Future<Object>>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service().executeLogin(key, UUID.randomUUID().toString(),
                                mapper.createObjectNode(), id -> {
                                    publications.incrementAndGet();
                                    entered.countDown();
                                    try {
                                        if (!release.await(10, TimeUnit.SECONDS)) {
                                            throw new AssertionError("Timeout releasing publisher");
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException(e);
                                    }
                                    return response(id);
                                });
                    } catch (IdempotencyException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var conflict = calls.stream().filter(Future::isDone).findFirst();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (conflict.isEmpty() && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                    conflict = calls.stream().filter(Future::isDone).findFirst();
                }
                assertTrue(conflict.isPresent(), "Second call should be rejected while first publishes");
            } finally {
                release.countDown();
            }
            var results = new java.util.ArrayList<Object>();
            for (var future : calls) results.add(future.get(5, TimeUnit.SECONDS));
            assertEquals(1, publications.get());
            assertEquals(1, results.stream().filter(CommonModelResponse.class::isInstance).count());
            var conflict = (IdempotencyException) results.stream()
                    .filter(IdempotencyException.class::isInstance).findFirst().orElseThrow();
            assertEquals("IDEMPOTENCY_IN_PROGRESS", conflict.getCode());
        }
    }

    @Test
    void uncertainPublicationIsNotRetriedWithinWindow() {
        String key = UUID.randomUUID().toString();
        assertThrows(KafkaPublicationException.class, () ->
                service().executeLogin(key, UUID.randomUUID().toString(),
                        mapper.createObjectNode(), id -> {
                            throw new KafkaPublicationException(id, new TimeoutException());
                        }));
        var ex = assertThrows(IdempotencyException.class, () ->
                service().executeLogin(key, UUID.randomUUID().toString(),
                        mapper.createObjectNode(), id -> {
                            fail("Uncertain message must not republish");
                            return null;
                        }));
        assertEquals(503, ex.getStatus().value());
        assertEquals("KAFKA_PUBLICATION_UNCONFIRMED", ex.getCode());
        assertEquals(60_000L, store.pttl(redisKey(key)));
    }

    @Test
    void completionPreservesTtlAndReplayDoesNotRenewIt() {
        String key = UUID.randomUUID().toString();
        service().executeLogin(key, UUID.randomUUID().toString(), mapper.createObjectNode(), id -> {
            assertEquals(60_000L, store.pttl(redisKey(key)));
            store.advanceMillis(55_000);
            return response(id);
        });
        assertEquals(5_000L, store.pttl(redisKey(key)));
        service().executeLogin(key, UUID.randomUUID().toString(), mapper.createObjectNode(), id -> {
            fail("No republish on replay");
            return null;
        });
        assertEquals(5_000L, store.pttl(redisKey(key)));
    }

    @Test
    void expiredClaimAllowsNewTransactionAndOldCompletionCannotOverride() throws Exception {
        String key = UUID.randomUUID().toString();
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var old = pool.submit(() -> service().executeLogin(
                    key, UUID.randomUUID().toString(), mapper.createObjectNode(), id -> {
                        claimed.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timeout releasing old publisher");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return response(id);
                    }));
            CommonModelResponse current;
            try {
                assertTrue(claimed.await(5, TimeUnit.SECONDS));
                store.advanceMillis(60_001);
                assertFalse(store.hasKey(redisKey(key)));
                current = service().executeLogin(key, UUID.randomUUID().toString(),
                        mapper.createObjectNode(), this::response);
            } finally {
                release.countDown();
            }
            assertNotEquals(old.get(5, TimeUnit.SECONDS).getTransactionId(),
                    current.getTransactionId());
            assertEquals(current, service().executeLogin(key, UUID.randomUUID().toString(),
                    mapper.createObjectNode(), id -> {
                        fail("Old completion cannot overwrite current cached result");
                        return null;
                    }));
        }
    }

    @Test
    void completionAfterDeletionDoesNotRecreateEntry() {
        String key = UUID.randomUUID().toString();
        service().executeLogin(key, UUID.randomUUID().toString(), mapper.createObjectNode(), id -> {
            store.delete(redisKey(key));
            return response(id);
        });
        assertFalse(store.hasKey(redisKey(key)));
    }

    @Test
    void invalidIdempotencyKeysNeverReachRedis() {
        for (String key : new String[] {null, "", " ", "a".repeat(129), "bad\nkey"}) {
            var ex = assertThrows(IdempotencyException.class, () ->
                    service().executeLogin(key, UUID.randomUUID().toString(),
                            mapper.createObjectNode(), this::response));
            assertEquals("INVALID_IDEMPOTENCY_KEY", ex.getCode());
        }
        verifyNoInteractions(store.redis);
    }

    @Test
    void redisFailureFailsClosed() {
        var redis = mock(StringRedisTemplate.class, invocation -> {
            if ("execute".equals(invocation.getMethod().getName())) {
                throw new RedisConnectionFailureException("offline");
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        var ex = assertThrows(IdempotencyException.class, () ->
                new IdempotencyService(redis, mapper).executeLogin("key",
                        UUID.randomUUID().toString(), mapper.createObjectNode(), id -> {
                            fail("Redis failures must not publish");
                            return null;
                        }));
        assertEquals(503, ex.getStatus().value());
        assertEquals("IDEMPOTENCY_STORE_UNAVAILABLE", ex.getCode());
    }
}

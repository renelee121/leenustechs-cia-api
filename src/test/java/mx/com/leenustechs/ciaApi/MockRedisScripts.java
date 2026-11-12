package mx.com.leenustechs.ciaApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Answers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.mockito.Mockito.mock;

/**
 * Mockito double for the Redis script boundary. It never opens a socket,
 * starts a process or talks to the DEV Valkey instance.
 *
 * This models the application's expected claim/complete contract, NOT
 * Redis/Lua correctness. Real Redis integration is verified separately.
 */
final class MockRedisScripts {

    private record Stored(String value, long expiresAtMillis) {}

    final StringRedisTemplate redis;
    private final Map<String, Stored> entries = new HashMap<>();
    private long nowMillis;

    MockRedisScripts() {
        redis = mock(StringRedisTemplate.class, invocation -> {
            if (!"execute".equals(invocation.getMethod().getName())) {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            Object[] args = invocation.getArguments();
            if (args.length < 3 || !(args[0] instanceof RedisScript<?> script)
                    || !(args[1] instanceof List<?> keys) || keys.size() != 1) {
                throw new AssertionError("Unexpected mocked Redis execution");
            }
            String key = (String) keys.get(0);
            String lua = script.getScriptAsString();
            synchronized (entries) {
                expireIfNeeded(key);
                Stored previous = entries.get(key);
                if (args.length == 3 && lua.contains("'PX', 60000")) {
                    if (previous != null) {
                        return previous.value();
                    }
                    entries.put(key, new Stored((String) args[2], nowMillis + 60_000));
                    return "";
                }
                if (args.length == 4 && lua.contains("'KEEPTTL'")) {
                    if (previous != null && previous.value().equals(args[2])) {
                        entries.put(key, new Stored((String) args[3], previous.expiresAtMillis()));
                        return 1L;
                    }
                    return 0L;
                }
                throw new AssertionError("Unexpected Redis script or arguments");
            }
        });
    }

    String get(String key) {
        synchronized (entries) {
            expireIfNeeded(key);
            Stored value = entries.get(key);
            return value == null ? null : value.value();
        }
    }

    boolean hasKey(String key) {
        return get(key) != null;
    }

    long pttl(String key) {
        synchronized (entries) {
            expireIfNeeded(key);
            Stored value = entries.get(key);
            return value == null ? -2 : value.expiresAtMillis() - nowMillis;
        }
    }

    void advanceMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Time cannot go backwards");
        }
        synchronized (entries) {
            nowMillis += millis;
        }
    }

    void shortenTtl(String key, long millis) {
        synchronized (entries) {
            expireIfNeeded(key);
            Stored value = entries.get(key);
            if (value == null) throw new AssertionError("No entry for key " + key);
            entries.put(key, new Stored(value.value(), nowMillis + millis));
        }
    }

    void delete(String key) {
        synchronized (entries) {
            entries.remove(key);
        }
    }

    private void expireIfNeeded(String key) {
        Stored value = entries.get(key);
        if (value != null && nowMillis >= value.expiresAtMillis()) {
            entries.remove(key);
        }
    }
}

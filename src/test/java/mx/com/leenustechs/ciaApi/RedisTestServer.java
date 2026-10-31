package mx.com.leenustechs.ciaApi;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.time.Duration;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Isolated real Redis process; never connects to or flushes a user's Redis. */
final class RedisTestServer implements AutoCloseable {
    final int port;
    final StringRedisTemplate redis;
    private final Process process;
    private final LettuceConnectionFactory connection;

    private RedisTestServer(int port, Process process) {
        this.port = port;
        this.process = process;
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
    }

    static RedisTestServer start() {
        try {
            int port;
            try (var socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            var log = Files.createTempFile("cia-redis-test-", ".log");
            var process = new ProcessBuilder(System.getenv().getOrDefault("REDIS_SERVER_BINARY", "redis-server"),
                    "--bind", "127.0.0.1", "--port", Integer.toString(port),
                    "--save", "", "--appendonly", "no", "--protected-mode", "yes")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            var server = new RedisTestServer(port, process);
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline && process.isAlive()) {
                try (var connection = server.connection.getConnection()) {
                    if ("PONG".equals(connection.ping())) {
                        return server;
                    }
                } catch (RuntimeException notReady) {
                    Thread.sleep(50);
                }
            }
            server.close();
            throw new IllegalStateException("Redis test server did not become ready; see " + log);
        } catch (Exception failure) {
            throw new IllegalStateException("Tests require redis-server >= 6 on PATH or REDIS_SERVER_BINARY",
                    failure);
        }
    }

    @Override
    public void close() {
        connection.destroy();
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException failure) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}

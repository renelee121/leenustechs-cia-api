package mx.com.leenustechs.ciaApi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = mx.com.leenustechs.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.application.name=cia-api-test",
        "kafka.username=", "kafka.password=", "kafka.bootstrap.servers=",
        "management.health.kafka.enabled=false"
})
class ApplicationReadinessTest {
    private static final RedisTestServer server = RedisTestServer.start();

    @org.springframework.test.context.DynamicPropertySource
    static void redisProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> server.port);
        registry.add("spring.data.redis.username", () -> "");
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    @org.junit.jupiter.api.AfterAll
    static void stopRedis() { server.close(); }

    @Autowired private Environment environment;
    @Autowired private JsonMapper mapper;
    @MockitoBean private KafkaTemplate<String, Object> template;

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private HttpResponse<String> login(HttpClient client, String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri("/config-api/v1/activation/login"))
                .header("Content-Type", "application/json");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void compatibleApplicationStartsAndHttpRetriesPublishOnce() throws Exception {
        assertEquals("true", environment.getProperty("spring.cloud.compatibility-verifier.enabled", "true"));
        try (var client = HttpClient.newHttpClient()) {
            var health = client.send(HttpRequest.newBuilder(uri("/actuator/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertEquals("UP", mapper.readTree(health.body()).get("status").asString());

            when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
            String key = UUID.randomUUID().toString();
            var first = login(client, key, "{\"payload\":{\"user\":\"alice\"}}");
            var replay = login(client, key, "{\"payload\":{\"user\":\"alice\"}}");
            assertEquals(202, first.statusCode());
            assertEquals(202, replay.statusCode());
            assertEquals(mapper.readTree(first.body()), mapper.readTree(replay.body()));
            assertEquals(409, login(client, key, "{\"payload\":{\"user\":\"bob\"}}").statusCode());
            assertEquals(400, login(client, null, "{\"payload\":{}}").statusCode());
            verify(template, times(1)).send(any(ProducerRecord.class));
        }
    }
    @Test
    void uncertainHttpRetriesRemainBlockedWithoutAnotherSend() throws Exception {
        when(template.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.failedFuture(new TimeoutException("ACK unavailable")));
        try (var client = HttpClient.newHttpClient()) {
            String key = UUID.randomUUID().toString();
            var first = login(client, key, "{\"payload\":{}}");
            var retry = login(client, key, "{\"payload\":{}}");
            assertEquals(503, first.statusCode());
            assertEquals(503, retry.statusCode());
            assertEquals(mapper.readTree(first.body()).get("transactionId"),
                    mapper.readTree(retry.body()).get("transactionId"));
            verify(template, times(1)).send(any(ProducerRecord.class));
        }
    }

}

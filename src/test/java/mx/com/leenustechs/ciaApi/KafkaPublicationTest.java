package mx.com.leenustechs.ciaApi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

import mx.com.leenustechs.ciaApi.business.adapters.in.ConfigApiProcessor;
import mx.com.leenustechs.ciaApi.business.adapters.out.KafkaProducerAdapter;
import mx.com.leenustechs.ciaApi.business.services.impl.CommandDispatcherServiceImpl;
import mx.com.leenustechs.ciaApi.business.useCases.CommonEventUseCase;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.KafkaPublicationException;
import mx.com.leenustechs.ciaApi.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.ciaApi.config.KafkaConfig;
import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import mx.com.leenustechs.ciaApi.rest.ConfigApi;
import mx.com.leenustechs.ciaApi.rest.GlobalExceptionHandler;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KafkaPublicationTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);

    private KafkaProducerAdapter adapter() {
        var adapter = new KafkaProducerAdapter(template);
        ReflectionTestUtils.setField(adapter, "applicationName", "cia-api");
        return adapter;
    }

    private MockMvc api() {
        var useCase = new CommonEventUseCase(adapter());
        var processor = new ConfigApiProcessor(new CommandDispatcherServiceImpl(List.of(useCase)),
                Mappers.getMapper(CommonModelMapper.class));
        return MockMvcBuilders.standaloneSetup(new ConfigApi(processor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new mx.com.leenustechs.ciaApi.business.adapters.in.ApiInterceptor()).build();
    }

    @Test
    void waitsForBrokerAcknowledgement() throws Exception {
        var acknowledgement = new CompletableFuture<SendResult<String, Object>>();
        var sent = new CountDownLatch(1);
        when(template.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            sent.countDown();
            return acknowledgement;
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var publication = executor.submit(() -> adapter().publish("topic", "tx", new Object()));
            try {
                assertTrue(sent.await(5, TimeUnit.SECONDS));
                assertFalse(publication.isDone(), "Publication must wait for Kafka acknowledgement");
                acknowledgement.complete(null);
                publication.get(5, TimeUnit.SECONDS);
            } finally {
                acknowledgement.completeExceptionally(new IllegalStateException("cleanup"));
            }
        }
    }

    @Test
    void successfulHttpResponseDoesNotMutatePublishedPayload() throws Exception {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        api().perform(post("/config-api/v1/activation/login").contentType("application/json")
                .content("{\"payload\":{\"user\":\"alice\",\"status\":\"original\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payload.status").value("success"));
        var record = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(record.capture());
        var event = (CommonModel) record.getValue().value();
        assertEquals("original", event.getPayload().get("status").asString());
        assertEquals("cia-api", new String(record.getValue().headers().lastHeader("origin-service").value(),
                StandardCharsets.UTF_8));
    }

    @Test
    void failedAcknowledgementReturnsServiceUnavailable() throws Exception {
        when(template.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        api().perform(post("/config-api/v1/activation/login").contentType("application/json")
                .content("{\"payload\":{}}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KAFKA_PUBLICATION_UNCONFIRMED"));
    }

    @Test
    void synchronousProducerFailureReturnsServiceUnavailable() throws Exception {
        when(template.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("creation failed"));
        api().perform(post("/config-api/v1/activation/login").contentType("application/json")
                .content("{\"payload\":{}}")).andExpect(status().isServiceUnavailable());
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgementTimeoutIsPropagated() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(future);
        when(future.get(35, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        assertInstanceOf(TimeoutException.class,
                assertThrows(KafkaPublicationException.class,
                        () -> adapter().publish("topic", "tx", new Object())).getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptionIsPreserved() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(future);
        when(future.get(35, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThrows(KafkaPublicationException.class,
                    () -> adapter().publish("topic", "tx", new Object()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidPayloadsReturnBadRequestWithoutPublishing() throws Exception {
        var api = api();
        for (var body : List.of("{}", "{\"payload\":null}", "{\"payload\":[]}",
                "{\"payload\":\"text\"}", "{\"payload\":1}", "{\"payload\":true}")) {
            api.perform(post("/config-api/v1/activation/login").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        }
        verifyNoInteractions(template);
    }

    @Test
    void requestPayloadIsUnchangedAfterUseCase() {
        var payload = mapper.createObjectNode().put("user", "alice");
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var response = new CommonEventUseCase(adapter()).execute(
                new CommonModel("tx", "CIA_API", OperationType.LOGIN, payload));
        assertFalse(payload.has("status"));
        assertNotSame(payload, response.getPayload());
    }

    @Test
    void scramJaasCredentialsAreEscapedAndParseable() {
        String user = "a\"b\\c";
        String password = "p\";\\word";
        var config = new KafkaConfig(user, password, "localhost:9092");
        var properties = new java.util.HashMap<String, Object>();
        properties.put("sasl.jaas.config", new org.apache.kafka.common.config.types.Password(config.getSaslJaasConfig()));
        var context = org.apache.kafka.common.security.JaasContext.loadClientContext(properties);
        var entry = context.configuration().getAppConfigurationEntry(context.name())[0];
        assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule", entry.getLoginModuleName());
        assertEquals(user, entry.getOptions().get("username"));
        assertEquals(password, entry.getOptions().get("password"));
    }

    @Test
    void factorySuppliesSerializerWithConfiguredMapper() {
        var config = new KafkaConfig("test", "test", "localhost:9092");
        var factory = (DefaultKafkaProducerFactory<String, Object>) config.producerFactory(mapper);
        try {
            var serializer = factory.getValueSerializerSupplier().get();
            var original = mapper.createObjectNode().put("field", "value");
            assertEquals(original, mapper.readTree(serializer.serialize("topic", original)));
            assertEquals("all", factory.getConfigurationProperties().get("acks"));
            assertEquals("SCRAM-SHA-512", factory.getConfigurationProperties().get("sasl.mechanism"));
        } finally {
            factory.destroy();
        }
    }
}

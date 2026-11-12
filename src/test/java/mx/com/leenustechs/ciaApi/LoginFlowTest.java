package mx.com.leenustechs.ciaApi;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import mx.com.leenustechs.ciaApi.business.adapters.in.ApiInterceptor;
import mx.com.leenustechs.ciaApi.business.adapters.in.ConfigApiProcessor;
import mx.com.leenustechs.ciaApi.business.adapters.out.KafkaProducerAdapter;
import mx.com.leenustechs.ciaApi.business.services.IdempotencyService;
import mx.com.leenustechs.ciaApi.business.services.impl.CommandDispatcherServiceImpl;
import mx.com.leenustechs.ciaApi.business.useCases.CommonEventUseCase;
import mx.com.leenustechs.ciaApi.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.ciaApi.rest.ConfigApi;
import mx.com.leenustechs.ciaApi.rest.GlobalExceptionHandler;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LoginFlowTest {

    private final MockRedisScripts store = new MockRedisScripts();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

    private MockMvc mvc() {
        var producer = new KafkaProducerAdapter(kafka);
        ReflectionTestUtils.setField(producer, "applicationName", "cia-api");
        var useCase = new CommonEventUseCase(producer);
        var processor = new ConfigApiProcessor(
                new CommandDispatcherServiceImpl(List.of(useCase)),
                Mappers.getMapper(CommonModelMapper.class),
                new IdempotencyService(store.redis, mapper));
        return MockMvcBuilders.standaloneSetup(new ConfigApi(processor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new ApiInterceptor())
                .build();
    }

    private MvcResult login(MockMvc mvc, String key, String body) throws Exception {
        var request = post("/config-api/v1/activation/login")
                .contentType("application/json")
                .content(body);
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request).andReturn();
    }

    @Test
    void replaysSameTransactionAndPublishesOnlyOnce() throws Exception {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var http = mvc();
        String key = UUID.randomUUID().toString();
        var first = login(http, key, "{\"payload\":{\"user\":\"alice\"}}");
        var replay = login(http, key, "{\"payload\":{\"user\":\"alice\"}}");
        assertEquals(202, first.getResponse().getStatus());
        assertEquals(202, replay.getResponse().getStatus());
        assertEquals(mapper.readTree(first.getResponse().getContentAsString()),
                mapper.readTree(replay.getResponse().getContentAsString()));
        assertEquals(409, login(http, key, "{\"payload\":{\"user\":\"bob\"}}")
                .getResponse().getStatus());
        assertEquals(400, login(http, null, "{\"payload\":{}}")
                .getResponse().getStatus());

        var published = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(1)).send(published.capture());
        assertEquals(mapper.readTree(first.getResponse().getContentAsString())
                        .get("transactionId").asString(),
                published.getValue().key());
    }

    @Test
    void uncertainKafkaAcknowledgmentRemainsBlockedOnRetry() throws Exception {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.failedFuture(new TimeoutException("ACK unavailable")));
        var http = mvc();
        String key = UUID.randomUUID().toString();
        var first = login(http, key, "{\"payload\":{}}");
        var second = login(http, key, "{\"payload\":{}}");
        assertEquals(503, first.getResponse().getStatus());
        assertEquals(503, second.getResponse().getStatus());
        assertEquals(mapper.readTree(first.getResponse().getContentAsString()).get("transactionId"),
                mapper.readTree(second.getResponse().getContentAsString()).get("transactionId"));
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }
}

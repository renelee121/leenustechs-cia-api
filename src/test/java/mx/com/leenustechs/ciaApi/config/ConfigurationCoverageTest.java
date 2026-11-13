package mx.com.leenustechs.ciaApi.config;

import feign.RequestTemplate;
import java.util.List;
import mx.com.leenustechs.ciaApi.business.adapters.in.ApiInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationCoverageTest {

    @Test
    void feignInterceptorSetsJsonRequestAndAcceptHeaders() {
        var template = new RequestTemplate();

        new FeingConfig().requestInterceptor().apply(template);

        assertEquals(List.of("application/json"),
                List.copyOf(template.headers().get("Content-Type")));
        assertEquals(List.of("application/json"),
                List.copyOf(template.headers().get("Accept")));
    }

    @Test
    void mvcConfigurationRegistersTheApiInterceptor() {
        var interceptor = mock(ApiInterceptor.class);
        var registry = mock(InterceptorRegistry.class);

        new InterceptorConfig(interceptor).addInterceptors(registry);

        verify(registry).addInterceptor(interceptor);
        verifyNoMoreInteractions(registry);
    }

    @Test
    void jacksonMapperAcceptsUnknownJsonProperties() {
        var mapper = new JacksonConfig().objectMapper();

        var payload = mapper.readValue(
                "{\"name\":\"alice\",\"unexpected\":42}", SamplePayload.class);

        assertEquals("alice", payload.name());
    }

    public record SamplePayload(String name) {}
}

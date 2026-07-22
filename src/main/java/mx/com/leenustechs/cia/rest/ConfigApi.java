package mx.com.leenustechs.cia.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mx.com.leenustechs.cia.business.adapters.in.ConfigApiProcessor;
import mx.com.leenustechs.cia.models.requests.CommonModelRequest;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config-api/v1/activation")
@RequiredArgsConstructor
public class ConfigApi {

    private final ConfigApiProcessor configApiProcessor;

    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CommonModelResponse login(
            @RequestBody CommonModelRequest requestEvent,
            HttpServletRequest request
    ) {
        return configApiProcessor.processLogin(requestEvent, request);
    }
}
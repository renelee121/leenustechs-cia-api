package mx.com.leenustechs.ciaApi.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mx.com.leenustechs.ciaApi.business.adapters.in.ConfigApiProcessor;
import mx.com.leenustechs.ciaApi.models.requests.CommonModelRequest;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
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
    @ResponseBody
    public ResponseEntity<CommonModelResponse> login(
            @RequestBody CommonModelRequest requestEvent,
            HttpServletRequest request
    ) {
        return ResponseEntity.accepted().body(configApiProcessor.processLogin(requestEvent, request));
    }
}
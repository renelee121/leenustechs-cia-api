package mx.com.leenustechs.cia.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mx.com.leenustechs.cia.business.services.OperationTypeService;
import mx.com.leenustechs.cia.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.requests.CommonModelRequest;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import mx.com.leenustechs.cia.models.types.OperationType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/config-api/v1/activation")
@RequiredArgsConstructor
public class ConfigApi {

    private static final String PRODUCER = "CONFIG_API";

    private final OperationTypeService operationTypeService;
    private final CommonModelMapper commonModelMapper;

    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CommonModelResponse login(
            @RequestBody CommonModelRequest requestEvent,
            HttpServletRequest request
    ) {
        String transactionId = (String) request.getAttribute("transactionId");

        CommonModel event = commonModelMapper.toModel(
                requestEvent,
                transactionId,
                PRODUCER,
                OperationType.LOGIN
        );

        CommonModelResponse response =
                event.getCommand().execute(event, operationTypeService);

        if (response != null) {
            return response;
        }

        return buildNoResponseError(transactionId);
    }

    private CommonModelResponse buildNoResponseError(String transactionId) {
        ObjectNode errorPayload =
                JsonNodeFactory.instance.objectNode();

        errorPayload.put("error", "No response from operation");

        return new CommonModelResponse(
                transactionId,
                PRODUCER,
                OperationType.LOGIN,
                errorPayload
        );
    }
}
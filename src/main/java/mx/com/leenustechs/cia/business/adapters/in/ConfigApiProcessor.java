package mx.com.leenustechs.cia.business.adapters.in;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.business.services.OperationTypeService;
import mx.com.leenustechs.cia.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.requests.CommonModelRequest;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import mx.com.leenustechs.cia.models.types.OperationType;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigApiProcessor {

    private static final String PRODUCER = "CONFIG_API";
    private final OperationTypeService operationTypeService;
    private final CommonModelMapper commonModelMapper;

    public CommonModelResponse processLogin(CommonModelRequest requestEvent, HttpServletRequest request) {
        String transactionId = (String) request.getAttribute("transactionId");

        CommonModel event = commonModelMapper.toModel(
                requestEvent,
                transactionId,
                PRODUCER,
                OperationType.LOGIN);

        CommonModelResponse response = operationTypeService.execute(event);

        if (response != null) {
            return response;
        }

        return buildNoResponseError(transactionId);
    }

    private CommonModelResponse buildNoResponseError(String transactionId) {
        ObjectNode errorPayload = JsonNodeFactory.instance.objectNode();

        errorPayload.put("error", "No response from operation");

        return new CommonModelResponse(
                transactionId,
                PRODUCER,
                OperationType.LOGIN,
                errorPayload);
    }
}

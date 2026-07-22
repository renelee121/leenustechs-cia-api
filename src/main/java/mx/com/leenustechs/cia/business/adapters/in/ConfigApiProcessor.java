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

        if (response == null) {
            log.error("No response returned for event: {}", event);
            throw new IllegalStateException("No response returned for event: " + event);
        }

        return response;
    }
}

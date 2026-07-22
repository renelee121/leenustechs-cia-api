package mx.com.leenustechs.ciaApi.business.adapters.in;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.ciaApi.business.services.CommandDispatcherService;
import mx.com.leenustechs.ciaApi.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.requests.CommonModelRequest;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigApiProcessor {

    private static final String PRODUCER = "CIA_API";
    private final CommandDispatcherService operationTypeService;
    private final CommonModelMapper commonModelMapper;

    public CommonModelResponse processLogin(CommonModelRequest requestEvent, HttpServletRequest request) {
        String transactionId = (String) request.getAttribute("transactionId");

        CommonModel event = commonModelMapper.toModel(
                requestEvent,
                transactionId,
                PRODUCER,
                OperationType.LOGIN);

        CommonModelResponse response = operationTypeService.execute(event);

        return response;
    }
}

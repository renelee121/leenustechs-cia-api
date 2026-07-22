package mx.com.leenustechs.cia.business.useCases;

import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.business.utils.commons.EventOperation;
import mx.com.leenustechs.cia.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import mx.com.leenustechs.cia.models.types.OperationType;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommonEventUseCase implements EventOperation {
    private final CommonModelMapper commonModelMapper;
    @Override
    public CommonModelResponse execute(CommonModel event) {
        log.info("Executing common event use case for event: {}", event);
        return commonModelMapper.toResponse(event);
    }

    @Override
    public Set<OperationType> getEventTypes() {
        return Set.of(OperationType.LOGIN, OperationType.LOGOUT);
    }

}

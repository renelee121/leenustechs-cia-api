package mx.com.leenustechs.cia.business.useCases;

import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.business.utils.commons.EventOperation;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import mx.com.leenustechs.cia.models.types.OperationType;

@Slf4j
@Component
public class DefaultUseCase implements EventOperation {
    @Override
    public CommonModelResponse execute(CommonModel event) {
        log.warn("Executing default use case for event: {}", event);
        throw new UnsupportedOperationException("Operation not supported for this event type");
    }

    @Override
    public Set<OperationType> getEventTypes() {
        return Set.of(OperationType.UNKOWN);
    }

}

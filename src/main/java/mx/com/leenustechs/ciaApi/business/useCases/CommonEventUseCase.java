package mx.com.leenustechs.ciaApi.business.useCases;

import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.ciaApi.business.adapters.out.KafkaProducerAdapter;
import mx.com.leenustechs.ciaApi.business.utils.commons.EventOperation;
import mx.com.leenustechs.ciaApi.business.utils.mappers.CommonModelMapper;
import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.constants.KafkaTopics;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommonEventUseCase implements EventOperation {
    private final CommonModelMapper commonModelMapper;
    private final KafkaProducerAdapter kafkaProducerAdapter;

    @Override
    public CommonModelResponse execute(CommonModel event) {
        log.info("Executing common event use case for event: {}", event);
        kafkaProducerAdapter.publish(KafkaTopics.LEENUSTECHS_CIA_LOBBY, event.getTransactionId(), event);
        ObjectNode responseJson = (ObjectNode) event.getPayload();
        responseJson.put("status", "success");
        return commonModelMapper.toResponse(event);
    }

    @Override
    public Set<OperationType> getEventTypes() {
        return Set.of(OperationType.LOGIN, OperationType.LOGOUT);
    }

}

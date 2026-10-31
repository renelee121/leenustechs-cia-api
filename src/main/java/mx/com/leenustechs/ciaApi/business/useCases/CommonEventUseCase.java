package mx.com.leenustechs.ciaApi.business.useCases;

import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.ciaApi.business.adapters.out.KafkaProducerAdapter;
import mx.com.leenustechs.ciaApi.business.utils.commons.EventOperation;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.InvalidPayloadException;
import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.constants.KafkaTopics;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommonEventUseCase implements EventOperation {
    private final KafkaProducerAdapter kafkaProducerAdapter;

    @Override
    public CommonModelResponse execute(CommonModel event) {
        if (!(event.getPayload() instanceof ObjectNode payload)) {
            throw new InvalidPayloadException();
        }
        kafkaProducerAdapter.publish(KafkaTopics.LEENUSTECHS_CIA_LOBBY, event.getTransactionId(), event);
        ObjectNode responseJson = payload.deepCopy();
        responseJson.put("status", "success");
        return new CommonModelResponse(event.getTransactionId(), event.getProducer(),
                event.getCommand(), responseJson);
    }

    @Override
    public Set<OperationType> getEventTypes() {
        return Set.of(OperationType.LOGIN, OperationType.LOGOUT);
    }

}

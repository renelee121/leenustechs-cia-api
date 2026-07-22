package mx.com.leenustechs.ciaApi.business.services.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import mx.com.leenustechs.ciaApi.business.services.CommandDispatcherService;
import mx.com.leenustechs.ciaApi.business.utils.commons.EventOperation;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.EmptyOperationResponseException;
import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;

@Service
public class CommandDispatcherServiceImpl implements CommandDispatcherService{

    private final Map<OperationType, EventOperation> operationMap;

    public CommandDispatcherServiceImpl(List<EventOperation> operations){
        operationMap = new EnumMap<>(OperationType.class);
        for(EventOperation operation : operations)
            for(OperationType type : operation.getEventTypes())
                if(operationMap.containsKey(type))
                    throw new IllegalStateException("Duplicate operation type: " + type);
                else
                    operationMap.put(type, operation);
    }

    private EventOperation getOperation(OperationType command){
        EventOperation operation = operationMap.get(command);
        if(operation == null)
            throw new IllegalArgumentException("No operation found for command: " + command);
        return operation;
    }

    @Override
    public CommonModelResponse execute(CommonModel event){
        EventOperation operation = getOperation(event.getCommand());
        CommonModelResponse response = operation.execute(event);

        if (response == null) {
            throw new EmptyOperationResponseException(event);
        }

        return response;
    }
    
}

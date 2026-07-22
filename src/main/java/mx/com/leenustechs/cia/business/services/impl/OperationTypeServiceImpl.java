package mx.com.leenustechs.cia.business.services.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import mx.com.leenustechs.cia.business.services.OperationTypeService;
import mx.com.leenustechs.cia.business.utils.commons.EventOperation;
import mx.com.leenustechs.cia.models.types.OperationType;

@Service
public class OperationTypeServiceImpl implements OperationTypeService{

    private final Map<OperationType, EventOperation> operationMap;

    public OperationTypeServiceImpl(List<EventOperation> operations){
        operationMap = new EnumMap<>(OperationType.class);
        for(EventOperation operation : operations)
            for(OperationType type : operation.getEventTypes())
                if(operationMap.containsKey(type))
                    throw new IllegalStateException("Duplicate operation type: " + type);
                else
                    operationMap.put(type, operation);
    }

    public EventOperation getOperation(OperationType command){
        return operationMap.getOrDefault(command, operationMap.get(OperationType.UNKOWN));
    }
    
}

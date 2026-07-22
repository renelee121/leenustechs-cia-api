package mx.com.leenustechs.cia.business.services;

import mx.com.leenustechs.cia.business.utils.commons.EventOperation;
import mx.com.leenustechs.cia.models.types.OperationType;

public interface OperationTypeService {
    public EventOperation getOperation(OperationType command);
}

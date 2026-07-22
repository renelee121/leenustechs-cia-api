package mx.com.leenustechs.cia.business.services;

import mx.com.leenustechs.cia.business.utils.commons.EventOperation;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import mx.com.leenustechs.cia.models.types.OperationType;

public interface CommandDispatcherService {
    public EventOperation getOperation(OperationType command);
    public CommonModelResponse execute(CommonModel event);
}

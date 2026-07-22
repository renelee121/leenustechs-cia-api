package mx.com.leenustechs.cia.models.types;

import mx.com.leenustechs.cia.business.services.OperationTypeService;
import mx.com.leenustechs.cia.business.utils.commons.EventOperation;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;

public enum OperationType {

    LOGIN,
    LOGOUT,
    UNKOWN;

    public CommonModelResponse execute(CommonModel event, OperationTypeService operationTypeService){
        EventOperation operation = operationTypeService.getOperation(this);
        return operation.execute(event);
        
    }

}

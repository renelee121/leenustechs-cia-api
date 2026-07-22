package mx.com.leenustechs.cia.business.utils.commons;

import java.util.Set;

import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;
import mx.com.leenustechs.cia.models.types.OperationType;

public interface EventOperation {
    public CommonModelResponse execute(CommonModel event);
    public Set<OperationType> getEventTypes();
}

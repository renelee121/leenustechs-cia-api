package mx.com.leenustechs.ciaApi.business.utils.commons;

import java.util.Set;

import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;

public interface EventOperation {
    public CommonModelResponse execute(CommonModel event);
    public Set<OperationType> getEventTypes();
}

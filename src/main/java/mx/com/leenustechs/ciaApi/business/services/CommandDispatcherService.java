package mx.com.leenustechs.ciaApi.business.services;

import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;

public interface CommandDispatcherService {
    public CommonModelResponse execute(CommonModel event);
}

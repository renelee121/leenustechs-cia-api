package mx.com.leenustechs.cia.business.services;

import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.responses.CommonModelResponse;

public interface CommandDispatcherService {
    public CommonModelResponse execute(CommonModel event);
}

package mx.com.leenustechs.ciaApi.business.utils.mappers;

import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.requests.CommonModelRequest;
import mx.com.leenustechs.ciaApi.models.responses.CommonModelResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CommonModelMapper {

    @Mapping(target = "transactionId", source = "transactionId")
    @Mapping(target = "producer", source = "producer")
    @Mapping(target = "command", source = "command")
    @Mapping(target = "payload", source = "request.payload")
    CommonModel toModel(
            CommonModelRequest request,
            String transactionId,
            String producer,
            OperationType command
    );

    CommonModelResponse toResponse(CommonModel model);
}
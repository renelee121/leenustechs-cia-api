package mx.com.leenustechs.cia.models.responses;



import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.models.types.OperationType;
import tools.jackson.databind.JsonNode;

@Slf4j
@Data
public class CommonModelResponse{
    private final String transactionId;
    private final String producer;
    private final OperationType command;
    private final JsonNode payload;
}

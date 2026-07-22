package mx.com.leenustechs.cia.models;



import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.models.types.OperationType;
import tools.jackson.databind.JsonNode;

@Slf4j
@Data
public class CommonModel implements Event{
    private final String transactionId;
    private final String producer;
    private final OperationType command;
    private final JsonNode payload;
}

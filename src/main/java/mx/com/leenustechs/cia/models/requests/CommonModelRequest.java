package mx.com.leenustechs.cia.models.requests;



import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Slf4j
@Data
public class CommonModelRequest{
    private final String transactionId;
    private final JsonNode payload;
}

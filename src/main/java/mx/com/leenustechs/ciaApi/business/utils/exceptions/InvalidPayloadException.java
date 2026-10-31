package mx.com.leenustechs.ciaApi.business.utils.exceptions;

public class InvalidPayloadException extends RuntimeException {
    public InvalidPayloadException() {
        super("payload must be a JSON object");
    }
}

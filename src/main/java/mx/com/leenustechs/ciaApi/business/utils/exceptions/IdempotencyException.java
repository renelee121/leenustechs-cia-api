package mx.com.leenustechs.ciaApi.business.utils.exceptions;

import org.springframework.http.HttpStatus;

public class IdempotencyException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String transactionId;

    public IdempotencyException(HttpStatus status, String code, String message, String transactionId) {
        super(message);
        this.status = status;
        this.code = code;
        this.transactionId = transactionId;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getTransactionId() { return transactionId; }
}

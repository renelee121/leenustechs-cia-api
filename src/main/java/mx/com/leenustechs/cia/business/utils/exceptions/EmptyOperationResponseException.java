package mx.com.leenustechs.cia.business.utils.exceptions;

import lombok.Getter;
import mx.com.leenustechs.cia.models.CommonModel;
import mx.com.leenustechs.cia.models.types.OperationType;

import java.io.Serial;
import java.util.Objects;

@Getter
public class EmptyOperationResponseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String transactionId;
    private final OperationType operationType;

    public EmptyOperationResponseException(CommonModel event) {
        this(
                Objects.requireNonNull(event, "event cannot be null")
                        .getTransactionId(),
                event.getCommand()
        );
    }

    public EmptyOperationResponseException(
            String transactionId,
            OperationType operationType
    ) {
        super(buildMessage(transactionId, operationType));

        this.transactionId = transactionId;
        this.operationType = operationType;
    }

    private static String buildMessage(
            String transactionId,
            OperationType operationType
    ) {
        return String.format(
                "Transaction '%s' with operation '%s' returned no response",
                transactionId,
                operationType
        );
    }
}
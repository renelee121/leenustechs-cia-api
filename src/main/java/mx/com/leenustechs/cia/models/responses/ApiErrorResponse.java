package mx.com.leenustechs.cia.models.responses;

import java.time.Instant;

public record ApiErrorResponse(
        String transactionId,
        String code,
        String message,
        Instant timestamp
) {
}

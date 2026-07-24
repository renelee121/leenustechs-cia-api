package mx.com.leenustechs.ciaApi.models.records;

import java.time.Instant;

public record ApiErrorResponse(
        String transactionId,
        String code,
        String message,
        Instant timestamp
) {
}

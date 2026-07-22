package mx.com.leenustechs.cia.business.utils.exceptions.handlers;

import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.business.utils.exceptions.EmptyOperationResponseException;
import mx.com.leenustechs.cia.models.responses.ApiErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmptyOperationResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleEmptyOperationResponse(
            EmptyOperationResponseException exception
    ) {
        log.error(
                "Operation returned no response. transactionId={}, operation={}",
                exception.getTransactionId(),
                exception.getOperationType(),
                exception
        );

        ApiErrorResponse response = new ApiErrorResponse(
                exception.getTransactionId(),
                "EMPTY_OPERATION_RESPONSE",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
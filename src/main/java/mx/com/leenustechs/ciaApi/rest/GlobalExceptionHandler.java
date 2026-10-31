package mx.com.leenustechs.ciaApi.rest;

import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.EmptyOperationResponseException;
import mx.com.leenustechs.ciaApi.models.records.ApiErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.InvalidPayloadException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.KafkaPublicationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPayloadException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPayload(
            InvalidPayloadException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                (String) request.getAttribute("transactionId"), "INVALID_PAYLOAD",
                exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(KafkaPublicationException.class)
    public ResponseEntity<ApiErrorResponse> handleKafkaPublication(KafkaPublicationException exception) {
        log.error("Kafka publication unconfirmed. transactionId={}", exception.getTransactionId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiErrorResponse(
                exception.getTransactionId(), "KAFKA_PUBLICATION_UNCONFIRMED",
                exception.getMessage(), Instant.now()));
    }

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
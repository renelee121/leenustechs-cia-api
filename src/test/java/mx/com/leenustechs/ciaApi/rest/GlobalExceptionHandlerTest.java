package mx.com.leenustechs.ciaApi.rest;

import jakarta.servlet.http.HttpServletRequest;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.EmptyOperationResponseException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.IdempotencyException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.InvalidPayloadException;
import mx.com.leenustechs.ciaApi.business.utils.exceptions.KafkaPublicationException;
import mx.com.leenustechs.ciaApi.models.CommonModel;
import mx.com.leenustechs.ciaApi.models.records.ApiErrorResponse;
import mx.com.leenustechs.ciaApi.models.types.OperationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void emptyOperationResponseReturnsStructuredHttp500() {
        var exception = new EmptyOperationResponseException("tx-empty", OperationType.LOGIN);

        var response = handler.handleEmptyOperationResponse(exception);

        var body = assertError(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "tx-empty", "EMPTY_OPERATION_RESPONSE");
        assertEquals(exception.getMessage(), body.message());
    }

    @Test
    void invalidPayloadIncludesRequestTransactionId() {
        var request = mock(HttpServletRequest.class);
        when(request.getAttribute("transactionId")).thenReturn("tx-invalid");
        var exception = new InvalidPayloadException();

        var response = handler.handleInvalidPayload(exception, request);

        var body = assertError(response, HttpStatus.BAD_REQUEST,
                "tx-invalid", "INVALID_PAYLOAD");
        assertEquals(exception.getMessage(), body.message());
        verify(request).getAttribute("transactionId");
    }

    @Test
    void idempotencyConflictPreservesStatusCodeAndTransactionId() {
        var exception = new IdempotencyException(HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_CONFLICT", "different payload", "tx-conflict");

        var response = handler.handleIdempotency(exception);

        var body = assertError(response, HttpStatus.CONFLICT,
                "tx-conflict", "IDEMPOTENCY_KEY_CONFLICT");
        assertEquals("different payload", body.message());
    }

    @Test
    void kafkaPublicationFailureReturnsHttp503() {
        var exception = new KafkaPublicationException("tx-kafka",
                new IllegalStateException("ack unavailable"));

        var response = handler.handleKafkaPublication(exception);

        var body = assertError(response, HttpStatus.SERVICE_UNAVAILABLE,
                "tx-kafka", "KAFKA_PUBLICATION_UNCONFIRMED");
        assertEquals(exception.getMessage(), body.message());
    }

    @Test
    void emptyOperationExceptionCanBeBuiltFromEvent() {
        var event = new CommonModel("tx-event", "cia-api", OperationType.LOGOUT, null);

        var exception = new EmptyOperationResponseException(event);

        assertEquals("tx-event", exception.getTransactionId());
        assertEquals(OperationType.LOGOUT, exception.getOperationType());
        assertTrue(exception.getMessage().contains("tx-event"));
        assertTrue(exception.getMessage().contains("LOGOUT"));
    }

    @Test
    void emptyOperationExceptionRejectsNullEvent() {
        assertThrows(NullPointerException.class,
                () -> new EmptyOperationResponseException((CommonModel) null));
    }

    private static ApiErrorResponse assertError(ResponseEntity<ApiErrorResponse> response,
            HttpStatus status, String transactionId, String code) {
        assertEquals(status, response.getStatusCode());
        var body = assertNotNull(response.getBody());
        assertEquals(transactionId, body.transactionId());
        assertEquals(code, body.code());
        assertNotNull(body.timestamp());
        return body;
    }
}

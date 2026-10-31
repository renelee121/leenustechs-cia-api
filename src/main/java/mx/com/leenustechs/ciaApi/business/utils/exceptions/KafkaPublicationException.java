package mx.com.leenustechs.ciaApi.business.utils.exceptions;

public class KafkaPublicationException extends RuntimeException {
    private final String transactionId;

    public KafkaPublicationException(String transactionId, Throwable cause) {
        super("Kafka publication could not be confirmed", cause);
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }
}

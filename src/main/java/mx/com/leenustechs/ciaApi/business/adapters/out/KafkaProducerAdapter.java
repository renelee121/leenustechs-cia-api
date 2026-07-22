package mx.com.leenustechs.ciaApi.business.adapters.out;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducerAdapter {

    @Value("${spring.application.name}")
    private String applicationName;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String key, Object event) {

        log.info(
                "Publishing event to Kafka topic: {}, key: {}",
                topic,
                key);

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);

        record.headers().add(
                "origin-service",
                applicationName.getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {

                    if (ex != null) {
                        log.error(
                                "Error publishing Kafka event. topic: {}, key: {}",
                                topic,
                                key,
                                ex);
                        return;
                    }

                    log.debug(
                            "Kafka event published. topic: {}, partition: {}, offset: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }
}

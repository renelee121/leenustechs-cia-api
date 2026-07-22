package mx.com.leenustechs.cia.business.adapters.out;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    public void publish(String topic, String key, Object event){
        log.info("Publishing event to Kafka topic: {}, key: {}, event: {}", topic, key, event);
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        record.headers().add("origin-service", applicationName.getBytes());
        try{
            kafkaTemplate.send(record);
        }catch(Exception e){
            log.error("Error publishing event to Kafka topic: {}, key: {}, event: {}, error: {}", topic, key, event, e.getMessage(), e);
            throw e;
        }
    }
}

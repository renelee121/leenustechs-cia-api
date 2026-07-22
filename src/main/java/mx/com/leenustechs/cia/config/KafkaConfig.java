package mx.com.leenustechs.cia.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import lombok.extern.slf4j.Slf4j;
import mx.com.leenustechs.cia.business.utils.commons.CustomSerializer;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {
    public static final String SASL_JAAS_CONFIG = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"{0}\" password=\"{1}\";";

    private final String kafkaUsername;
    private final String kafkaPassword;
    private final String kafkaBootstrapServers;

    public KafkaConfig(
        @Value("${kafka.username}") String kafkaUsername,
        @Value("${kafka.password}") String kafkaPassword,
        @Value("${kafka.bootstrap.servers}") String kafkaBootstrapServers
    ){
        this.kafkaUsername = kafkaUsername;
        this.kafkaPassword = kafkaPassword;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }
    
    public String getSaslJaasConfig() {
        String jaasConfig = String.format(SASL_JAAS_CONFIG, kafkaUsername, kafkaPassword);
        log.info("SASL JAAS Config: {}", jaasConfig);
        return jaasConfig;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CustomSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

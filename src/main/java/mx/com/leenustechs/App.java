package mx.com.leenustechs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableFeignClients(basePackages = "mx.com.leenustechs.cia")
@ComponentScan(basePackages = "mx.com.leenustechs.cia")
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        log.info("Application started successfully.");
    }
}
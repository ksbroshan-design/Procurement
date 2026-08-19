package com.procurement.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTransactionManagement
public class AutonomousProcurementApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutonomousProcurementApplication.class, args);
    }
}

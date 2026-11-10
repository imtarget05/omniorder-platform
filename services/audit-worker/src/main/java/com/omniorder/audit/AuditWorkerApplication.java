package com.omniorder.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class AuditWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditWorkerApplication.class, args);
    }
}

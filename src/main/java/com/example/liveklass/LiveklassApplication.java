package com.example.liveklass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LiveklassApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiveklassApplication.class, args);
    }
}

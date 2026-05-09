package com.knowflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableRabbit
@SpringBootApplication
public class KnowFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowFlowApplication.class, args);
    }
}

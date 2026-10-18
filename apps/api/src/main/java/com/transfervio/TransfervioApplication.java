package com.transfervio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransfervioApplication {
    public static void main(String[] args) { SpringApplication.run(TransfervioApplication.class, args); }
}

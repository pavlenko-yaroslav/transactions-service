package ru.yaroslav_pavlenko.TransactionsRestApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransactionsRestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionsRestApiApplication.class, args);
    }
}

package br.com.conectabyte.knowly;

import org.springframework.boot.SpringApplication;

public class TestKnowlyApplication {

    public static void main(String[] args) {
        SpringApplication.from(KnowlyApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}

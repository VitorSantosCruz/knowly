package br.com.conectabyte.knowly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowlyApplication.class, args);
    }
}

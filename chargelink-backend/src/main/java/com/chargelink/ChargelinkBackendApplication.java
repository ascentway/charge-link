package com.chargelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChargelinkBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargelinkBackendApplication.class, args);
    }

}

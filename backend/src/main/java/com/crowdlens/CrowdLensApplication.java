package com.crowdlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CrowdLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrowdLensApplication.class, args);
    }
}

package com.crowdlens.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI crowdLensOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CrowdLens API")
                        .description(
                                "Aggregates authentic user opinions from social platforms and uses AI for structured analysis. "
                                        +
                                        "Search any product, service, or experience to get crowd-sourced insights powered by AI.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CrowdLens")
                                .url("https://github.com/anubhavbagri/CrowdLens")));
    }
}

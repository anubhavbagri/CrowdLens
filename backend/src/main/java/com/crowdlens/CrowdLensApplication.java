package com.crowdlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WHAT IT DOES:
 * Bootstraps the Spring Boot application and initializes the ApplicationContext.
 *
 * WHY IT'S NEEDED:
 * Serves as the main entry point to start the embedded web container (Tomcat) and
 * trigger component scanning across the package tree.
 *
 * HOW IT WORKS:
 * The {@link SpringApplication#run(Class, String[])} method delegates boot orchestration to Spring's
 * framework, loading configurations, scanning for components, and setting up dependency injection.
 *
 * SPRING ANNOTATIONS EXPLAINED:
 * - @SpringBootApplication: A convenience annotation that combines:
 *   - @SpringBootConfiguration: Designates this class as a configuration source.
 *   - @EnableAutoConfiguration: Tells Spring Boot to automatically configure beans based on classpath dependencies.
 *   - @ComponentScan: Scans for @Component, @Service, @Repository, and @Controller beans in the current package and sub-packages.
 */
@SpringBootApplication
public class CrowdLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrowdLensApplication.class, args);
    }
}


package com.crowdlens.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Configures HTTP timeouts for Spring AI's OpenAI RestClient.
 *
 * Spring AI 1.1.x does NOT expose connect/read timeout properties in application.yml.
 * The auto-configured RestClient uses Reactor Netty with ~10s read timeout by default,
 * which is too short for gpt-4o-mini with large prompts (27k+ chars → 30-60s inference).
 *
 * This RestClientCustomizer replaces the HTTP factory with explicit timeouts.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/discussions/2195">Spring AI #2195</a>
 */
@Configuration
public class OpenAiRestClientConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(120));  // 2 minutes for large prompts
            restClientBuilder.requestFactory(factory);
        };
    }
}

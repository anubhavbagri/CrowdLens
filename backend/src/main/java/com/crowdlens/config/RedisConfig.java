package com.crowdlens.config;

import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Forces Lettuce to use the JVM DNS resolver instead of Netty's async DNS client.
 * Netty's resolver sends raw UDP queries to 127.0.0.11:53 which times out in Docker.
 * The JVM resolver uses InetAddress (reads /etc/resolv.conf) and works correctly.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceJvmDnsResolver() {
        return builder -> builder.clientResources(
                DefaultClientResources.builder()
                        .dnsResolver(DnsResolvers.JVM_DEFAULT)
                        .build()
        );
    }
}

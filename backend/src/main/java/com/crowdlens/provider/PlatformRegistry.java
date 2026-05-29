package com.crowdlens.provider;

import com.crowdlens.model.dto.SocialPostDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * WHAT IT DOES:
 * Registry that holds all available {@link PlatformProvider} implementations, acting as a facade
 * to orchestrate and aggregate search crawl processes across platforms.
 *
 * WHY IT'S NEEDED:
 * Decouples the service execution logic from concrete social media crawl providers (e.g. Reddit, Twitter).
 * It enforces the Open/Closed Principle: you can register new crawler components by simply implementing
 * the provider interface, and this registry will automatically discover them without any code modification.
 *
 * HOW IT WORKS:
 * - Dependency Injection List Injection: Spring automatically resolves all beans that implement the {@link PlatformProvider} interface and injects them as a List into the constructor.
 * - Platform aggregation: searchAll(...) loops through the mapped providers and invokes their search processes. It catches exceptions per provider so that a single platform failure (e.g. Twitter down) doesn't abort the entire job (graceful degradation).
 *
 * SPRING ANNOTATIONS EXPLAINED:
 * - @Component: General-purpose stereotype annotation indicating that this class is a Spring-managed bean, registering it inside the IoC container.
 */
@Slf4j
@Component
public class PlatformRegistry {

    private final Map<String, PlatformProvider> providers;

    public PlatformRegistry(List<PlatformProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        PlatformProvider::getPlatformName,
                        p -> p,
                        (a, b) -> a,
                        LinkedHashMap::new));
        log.info("Registered {} platform providers: {}", providers.size(), providers.keySet());
    }

    /**
     * Searches all enabled platforms and aggregates results.
     */
    public List<SocialPostDto> searchAll(String query, int limitPerPlatform, int maxComments) {
        List<SocialPostDto> allPosts = new ArrayList<>();

        for (var entry : providers.entrySet()) {
            try {
                log.info("Searching platform: {} for query: '{}'", entry.getKey(), query);
                List<SocialPostDto> posts = entry.getValue().search(query, limitPerPlatform, maxComments);
                allPosts.addAll(posts);
                log.info("Platform {} returned {} posts", entry.getKey(), posts.size());
            } catch (Exception e) {
                log.error("Platform {} failed for query '{}': {}", entry.getKey(), query, e.getMessage());
                // Continue with other platforms — partial results > no results
            }
        }

        return allPosts;
    }

    /**
     * @return Names of all registered platforms
     */
    public List<String> getEnabledPlatforms() {
        return new ArrayList<>(providers.keySet());
    }

    /**
     * Runs health checks on all registered providers.
     */
    public Map<String, Boolean> healthCheckAll() {
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (var entry : providers.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().healthCheck());
            } catch (Exception e) {
                results.put(entry.getKey(), false);
            }
        }
        return results;
    }
}

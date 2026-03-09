package com.crowdlens.provider;

import com.crowdlens.model.dto.SocialPostDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all enabled PlatformProviders.
 * Delegates search to each provider and aggregates results.
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

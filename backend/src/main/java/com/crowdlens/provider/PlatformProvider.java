package com.crowdlens.provider;

import com.crowdlens.model.dto.SocialPostDto;

import java.util.List;

/**
 * Strategy interface for social media platform data providers.
 * Each platform (Reddit, Twitter, HN, etc.) implements this interface.
 * The PlatformRegistry delegates search calls to all enabled providers.
 */
public interface PlatformProvider {

    /**
     * @return Platform identifier, e.g. "reddit", "twitter"
     */
    String getPlatformName();

    /**
     * Search for posts matching the query.
     *
     * @param query       User's search string
     * @param limit       Maximum number of posts to return
     * @param maxComments Maximum number of comments to collect
     * @return List of normalized social posts
     */
    List<SocialPostDto> search(String query, int limit, int maxComments);

    /**
     * @return true if the platform API is reachable and healthy
     */
    boolean healthCheck();
}

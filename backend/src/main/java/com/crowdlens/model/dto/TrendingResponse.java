package com.crowdlens.model.dto;

import java.util.List;

/**
 * Response DTO for the /api/trending endpoint.
 * Provides discovery content for the landing page.
 */
public record TrendingResponse(
        List<TrendingItem> trending,
        List<CategoryItem> popularCategories
) {
    /**
     * A search item shown on the landing page.
     *
     * @param query    The original search query
     * @param score    Overall community score (0-100), null for unanalyzed items
     * @param category Product category assigned by AI (e.g. "Grooming", "Electronics")
     */
    public record TrendingItem(String query, Integer score, String category) {}

    /**
     * A popular product category with its search count and product names.
     *
     * @param name     Category name (e.g. "Electronics")
     * @param count    Number of unique products searched in this category
     * @param products Product query names in this category (for popover display)
     */
    public record CategoryItem(String name, long count, List<String> products) {}
}


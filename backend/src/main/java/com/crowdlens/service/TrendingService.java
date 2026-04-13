package com.crowdlens.service;

import com.crowdlens.model.dto.TrendingResponse;
import com.crowdlens.model.dto.TrendingResponse.CategoryItem;
import com.crowdlens.model.dto.TrendingResponse.TrendingItem;
import com.crowdlens.repository.SearchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides trending and popular-category data for the landing page.
 * Returns only real data from the database — no hardcoded fallbacks.
 * The frontend gracefully handles empty sections.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingService {

    private final SearchResultRepository searchResultRepo;

    /**
     * Returns trending searches and popular categories from the database.
     */
    public TrendingResponse getTrending() {
        List<TrendingItem> trending = loadTrending();
        List<CategoryItem> categories = loadCategories();

        log.debug("Trending: {} items, Categories: {} items",
                trending.size(), categories.size());

        return new TrendingResponse(trending, categories);
    }

    private List<TrendingItem> loadTrending() {
        try {
            return searchResultRepo.findTrendingByFrequency().stream()
                    .map(p -> new TrendingItem(p.getQuery(), p.getScore(), p.getCategory()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load trending data: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CategoryItem> loadCategories() {
        try {
            return searchResultRepo.findPopularCategories().stream()
                    .map(p -> {
                        List<String> products = searchResultRepo.findProductsByCategory(p.getName());
                        return new CategoryItem(p.getName(), p.getSearchCount(), products);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load category data: {}", e.getMessage());
            return List.of();
        }
    }
}

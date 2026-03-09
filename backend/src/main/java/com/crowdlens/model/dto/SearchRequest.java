package com.crowdlens.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Search request to analyze crowd opinions on a topic")
public record SearchRequest(
                @Schema(description = "The search query (product, service, experience, etc.)", example = "creatine supplement", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "Query cannot be blank") @Size(min = 2, max = 500, message = "Query must be between 2 and 500 characters") String query,

                @Schema(description = "Maximum number of posts to analyze (default: 10)", example = "10", minimum = "1", maximum = "50") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 50, message = "Limit cannot exceed 50") Integer limit,

                @Schema(description = "Maximum number of comments to collect across all posts (default: 50)", example = "50", minimum = "1", maximum = "100") @Min(value = 1, message = "maxComments must be at least 1") @Max(value = 100, message = "maxComments cannot exceed 100") Integer maxComments) {
        public int effectiveLimit() {
                return limit != null ? limit : 10;
        }

        public int effectiveMaxComments() {
                return maxComments != null ? maxComments : 50;
        }
}

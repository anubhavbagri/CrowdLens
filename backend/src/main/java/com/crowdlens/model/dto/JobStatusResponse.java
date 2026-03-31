package com.crowdlens.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStatusResponse(
        UUID jobId,
        String status,
        SearchResponse result,  // non-null only when status = COMPLETED
        String error            // non-null only when status = FAILED
) {}

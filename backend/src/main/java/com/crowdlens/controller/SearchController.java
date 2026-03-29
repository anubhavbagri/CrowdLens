package com.crowdlens.controller;

import com.crowdlens.model.dto.SearchRequest;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.entity.SearchJob;
import com.crowdlens.service.SearchOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Search", description = "Search and analyze social media opinions")
public class SearchController {

    private final SearchOrchestrator orchestrator;

    /**
     * Submit a search request.
     *
     * Cache hit  → 200 with full SearchResponse (field "cached": true).
     * Cache miss → 202 with { "jobId": "...", "status": "PENDING" }.
     *              Client should poll GET /api/search/{jobId} for the result.
     */
    @Operation(summary = "Analyze crowd opinions",
            description = "Searches Reddit for posts matching the query and runs AI analysis. " +
                    "Returns the full result immediately on a cache hit (HTTP 200). " +
                    "On a cache miss, queues the job and returns a jobId (HTTP 202) — " +
                    "poll GET /api/search/{jobId} to retrieve the result when ready.")
    @ApiResponse(responseCode = "200", description = "Cache hit — full result returned immediately",
            content = @Content(schema = @Schema(implementation = SearchResponse.class)))
    @ApiResponse(responseCode = "202", description = "Job queued — poll /api/search/{jobId} for the result")
    @ApiResponse(responseCode = "400", description = "Invalid request (blank or too long query)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = SearchRequest.class),
                    examples = @ExampleObject(name = "Example search",
                            value = "{\"query\": \"creatine supplement\", \"limit\": 10, \"maxComments\": 50}")))
    @PostMapping("/search")
    public ResponseEntity<?> search(@Valid @RequestBody SearchRequest request) {
        log.info("POST /api/search — query: '{}'", request.query());

        // Fast path: return cached result immediately
        Optional<SearchResponse> cached = orchestrator.getCachedResult(request.query());
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        // Slow path: queue a job and let the client poll
        UUID jobId = orchestrator.submitSearch(request);
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "PENDING"
        ));
    }

    /**
     * Poll for the result of a previously submitted search job.
     *
     * Response shape:
     *   PENDING / IN_PROGRESS → { "jobId": "...", "status": "..." }
     *   COMPLETED             → { "jobId": "...", "status": "COMPLETED", "result": { ...SearchResponse... } }
     *   FAILED                → { "jobId": "...", "status": "FAILED", "error": "..." }
     *   Not found             → 404
     */
    @Operation(summary = "Poll job status",
            description = "Returns the current status of a search job. " +
                    "When status is COMPLETED the full analysis result is included in the 'result' field.")
    @ApiResponse(responseCode = "200", description = "Job status returned")
    @ApiResponse(responseCode = "404", description = "Job not found")
    @GetMapping("/search/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable UUID jobId) {
        log.debug("GET /api/search/{}", jobId);

        Optional<SearchJob> jobOpt = orchestrator.getJob(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SearchJob job = jobOpt.get();

        return switch (job.getStatus()) {
            case PENDING, IN_PROGRESS -> ResponseEntity.ok(Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus().name()
            ));
            case COMPLETED -> {
                // Re-check cache for the serialized response; it was written there on completion
                Optional<SearchResponse> result = orchestrator.getCachedResult(job.getQuery());
                if (result.isPresent()) {
                    yield ResponseEntity.ok(Map.of(
                            "jobId", job.getId(),
                            "status", "COMPLETED",
                            "result", result.get()
                    ));
                }
                // Cache may have been evicted — return status only
                yield ResponseEntity.ok(Map.of(
                        "jobId", job.getId(),
                        "status", "COMPLETED"
                ));
            }
            case FAILED -> ResponseEntity.ok(Map.of(
                    "jobId", job.getId(),
                    "status", "FAILED",
                    "error", job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error"
            ));
        };
    }
}

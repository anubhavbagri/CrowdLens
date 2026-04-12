package com.crowdlens.controller;

import com.crowdlens.model.dto.JobStatusResponse;
import com.crowdlens.model.dto.SearchRequest;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.entity.SearchJob;
import com.crowdlens.service.LoadingHintsService;
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

import java.util.List;
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
    private final LoadingHintsService loadingHintsService;

    @Operation(
        summary = "Analyze crowd opinions",
        description = "Cache hit → HTTP 200 with full result immediately. " +
                      "Cache miss → HTTP 202 Accepted with {jobId, status:PENDING}. " +
                      "Poll GET /api/search/{jobId} to check progress and retrieve result.")
    @ApiResponse(responseCode = "200", description = "Cache hit — full result returned",
            content = @Content(schema = @Schema(implementation = SearchResponse.class)))
    @ApiResponse(responseCode = "202", description = "Job queued — poll /api/search/{jobId}",
            content = @Content(schema = @Schema(implementation = JobStatusResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request (blank or too long query)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = SearchRequest.class),
                    examples = @ExampleObject(name = "Example search",
                            value = "{\"query\": \"creatine supplement\", \"limit\": 10, \"maxComments\": 50}")))
    @PostMapping("/search")
    public ResponseEntity<?> search(@Valid @RequestBody SearchRequest request) {
        log.info("POST /api/search — query: '{}'", request.query());

        // Fast path: return cached result immediately (HTTP 200)
        Optional<SearchResponse> cached = orchestrator.getCachedResult(request.query());
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        // Slow path: persist job (commits to SQLite), then enqueue to Redis.
        // The two-step split is intentional: persistJob() is @Transactional and commits the DB
        // row before enqueueJob() publishes the Redis message, preventing a race where the
        // listener dequeues the message before the row is visible.
        UUID jobId = orchestrator.persistJob(request);
        orchestrator.enqueueJob(jobId);

        log.info("Job {} queued for query: '{}'", jobId, request.query());
        return ResponseEntity.accepted().body(
                JobStatusResponse.builder()
                        .jobId(jobId)
                        .status(SearchJob.Status.PENDING.name())
                        .build());
    }

    @Operation(
        summary = "Poll job status",
        description = "Returns current status of a queued search job. " +
                      "PENDING/IN_PROGRESS → still processing. " +
                      "COMPLETED → includes full result. " +
                      "FAILED → includes error message.")
    @ApiResponse(responseCode = "200", description = "Job status",
            content = @Content(schema = @Schema(implementation = JobStatusResponse.class)))
    @ApiResponse(responseCode = "404", description = "Job not found")
    @GetMapping("/search/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        log.debug("GET /api/search/{}", jobId);

        Optional<SearchJob> jobOpt = orchestrator.getJob(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SearchJob job = jobOpt.get();
        return switch (job.getStatus()) {
            case PENDING, IN_PROGRESS -> ResponseEntity.ok(
                    JobStatusResponse.builder()
                            .jobId(job.getId())
                            .status(job.getStatus().name())
                            .build());
            case COMPLETED -> ResponseEntity.ok(
                    JobStatusResponse.builder()
                            .jobId(job.getId())
                            .status(SearchJob.Status.COMPLETED.name())
                            .result(orchestrator.getResultForJob(job).orElse(null))
                            .build());
            case FAILED -> ResponseEntity.ok(
                    JobStatusResponse.builder()
                            .jobId(job.getId())
                            .status(SearchJob.Status.FAILED.name())
                            .error(job.getErrorMessage())
                            .build());
        };
    }
    @Operation(
        summary = "Get contextual loading hints",
        description = "Returns 4–5 short, AI-generated loading messages specific to the query. " +
                      "Returns an empty list when Gemini is unavailable — the frontend handles degradation.")
    @ApiResponse(responseCode = "200", description = "List of hint strings + source (gemini|none)")
    @GetMapping("/loading-hints")
    public ResponseEntity<Map<String, Object>> getLoadingHints(
            @RequestParam(name = "q", defaultValue = "") String query) {

        if (query.isBlank()) {
            return ResponseEntity.ok(Map.of("hints", List.of(), "source", "none"));
        }

        List<String> hints = loadingHintsService.generateWithGemini(query);
        if (hints != null) {
            return ResponseEntity.ok(Map.of("hints", hints, "source", "gemini"));
        }

        // Gemini unavailable — return empty, frontend shows its own generic hints
        return ResponseEntity.ok(Map.of("hints", List.of(), "source", "none"));
    }
}

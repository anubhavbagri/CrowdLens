package com.crowdlens.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published after a SearchJob row is committed to the database.
 * The SearchJobListener picks this up and runs the full pipeline
 * (scrape → AI → persist → cache) synchronously, one job at a time.
 */
public class SearchJobCreatedEvent extends ApplicationEvent {

    private final UUID jobId;

    public SearchJobCreatedEvent(Object source, UUID jobId) {
        super(source);
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}

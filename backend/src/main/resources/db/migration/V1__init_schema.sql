-- V1__init_schema.sql
-- CrowdLens initial schema

CREATE TABLE search_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query            VARCHAR(500)  NOT NULL,
    query_normalized VARCHAR(500)  NOT NULL,
    overall_score    INTEGER       CHECK (overall_score BETWEEN 0 AND 100),
    overall_verdict  TEXT,
    analysis         JSONB         NOT NULL,
    source_platforms TEXT[]        DEFAULT '{}',
    post_count       INTEGER       DEFAULT 0,
    created_at       TIMESTAMP     DEFAULT NOW(),
    expires_at       TIMESTAMP     DEFAULT NOW() + INTERVAL '7 days'
);

CREATE INDEX idx_search_query_norm ON search_results(query_normalized);
CREATE INDEX idx_search_created    ON search_results(created_at DESC);


CREATE TABLE social_posts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform         VARCHAR(50)   NOT NULL,
    platform_id      VARCHAR(100)  UNIQUE NOT NULL,
    search_result_id UUID          REFERENCES search_results(id) ON DELETE CASCADE,
    source           VARCHAR(200),
    title            TEXT,
    body             TEXT,
    score            INTEGER       DEFAULT 0,
    permalink        VARCHAR(500),
    posted_at        TIMESTAMP,
    scraped_at       TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_posts_platform ON social_posts(platform, platform_id);
CREATE INDEX idx_posts_search   ON social_posts(search_result_id);


CREATE TABLE scrape_cursors (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform         VARCHAR(50)   NOT NULL,
    query_normalized VARCHAR(500)  NOT NULL,
    search_result_id UUID          REFERENCES search_results(id) ON DELETE SET NULL,
    last_item_date   TIMESTAMP,
    recent_ids       JSONB         DEFAULT '[]',
    updated_at       TIMESTAMP     DEFAULT NOW(),
    UNIQUE(platform, query_normalized)
);

CREATE INDEX idx_cursor_lookup ON scrape_cursors(platform, query_normalized);

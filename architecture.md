# CrowdLens Architecture

## Overview

CrowdLens is a product opinion analysis engine that aggregates Reddit discussions and uses AI to produce structured, dynamic verdicts. The architecture is built for a **single low-powered VM** (2 vCPU / 1 GB RAM) and optimized for sequential processing, minimal memory footprint, and graceful degradation.

---

## System Topology

```
User Browser (Vercel)
        │
        │  POST /api/search
        ▼
┌─────────────────────────────────────────────────┐
│             Spring Boot 3.2 (OCI VM)            │
│                                                 │
│  SearchController                               │
│      │                                          │
│      ├── Cache HIT  →  HTTP 200 immediately     │
│      │       ↑                                  │
│      │   DynamoDB (AWS) ──────────────────────► │
│      │                                          │
│      └── Cache MISS → UUID jobId → HTTP 202     │
│                │                                │
│          SearchOrchestrator                     │
│                │  persistJob()    → SQLite       │
│                │  enqueueJob()    → Redis        │
│                                                 │
│  SearchJobListener (@RqueueListener)            │
│    concurrency=1, numRetries=0                  │
│        │  searchAll()   → Reddit API / scraper  │
│        │  analyze()     → OpenAI (primary AI)   │
│        │  fetchImage()  → Reddit/Amazon         │
│        │  persist()     → SQLite (lean row)     │
│        │  cache()       → DynamoDB (full JSON)  │
│        │  seedCompetitors() → SQLite            │
│        └  job.COMPLETED → resultJson stored     │
│                                                 │
│  GET /api/search/{jobId}  (polling)             │
│    → PENDING/IN_PROGRESS/COMPLETED/FAILED       │
└─────────────────────────────────────────────────┘
        │
        ▼
  Frontend polls every 2s (MAX 90 polls = 3 min)
```

---

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| **Application server** | Spring Boot 3.2 / Java 17 | REST API + async job worker |
| **Job queue** | Redis 7 + RQueue | Decouple HTTP request from slow pipeline |
| **Primary DB** | SQLite (WAL mode) | Lightweight job state + result index |
| **AI cache** | AWS DynamoDB | Full JSON response cache with TTL |
| **Primary AI** | OpenAI (configurable model) | Product analysis, 4 dynamic metrics |
| **Secondary AI** | Google Gemini | Loading hints (lightweight, non-critical) |
| **Frontend** | Next.js 14 / Vercel | UI + share card export |
| **Scraping** | Reddit OAuth2 + `.json` fallback | Community post collection |

---

## Async Job Flow (The Core Pattern)

The most important design decision: **HTTP request ≠ analysis**.

```
POST /api/search
  └─ cache hit?  → YES → HTTP 200, full result
  └─ cache hit?  → NO  → HTTP 202, { jobId, status: "PENDING" }

GET /api/search/{jobId}   (polled by frontend every 2s)
  └─ PENDING      → still queued
  └─ IN_PROGRESS  → scraping/analyzing
  └─ COMPLETED    → full SearchResponse included
  └─ FAILED       → error message
```

**Why two steps for the slow path?**
`persistJob()` is `@Transactional` — it commits the SQLite row before `enqueueJob()` publishes the Redis message. This prevents a race where the RQueue listener dequeues the message before the `SearchJob` row is visible to the transaction.

---

## Data Split: SQLite vs DynamoDB

| Stored in | What | Why |
|---|---|---|
| **SQLite** | `SearchJob` (status, jobId, query) | Job lifecycle tracking while Redis message is in-flight |
| **SQLite** | `SearchResult` (lean: query, score, category, imageUrl) | Fast competitor lookups + trending queries |
| **SQLite** | `SocialPost` (per-post index) | Deduplication across repeated queries |
| **DynamoDB** | Full `SearchResponse` JSON | AI output is large (~10 KB); TTL auto-expiry; no schema |

---

## SearchJobListener Pipeline (Sequential)

`concurrency="1"` is intentional — one job runs at a time on the 1 GB server.

```
onMessage(SearchJobMessage)
    │
    ├─ [1] searchAll() — Reddit scrape (10 posts × 50 comments max)
    ├─ [2] aiEngine.analyze() — OpenAI → dynamic 4-metric verdict
    ├─ [3] imageResolutionService — Reddit AI-validated → Amazon fallback → null
    ├─ [4] toBase64DataUri() — encode image for CORS-safe share card
    ├─ [5] Save lean SearchResult to SQLite
    ├─ [6] Save deduplicated SocialPosts to SQLite
    ├─ [7] Build full SearchResponse → cache in DynamoDB
    ├─ [8] seedCompetitorsIfNeeded() → SQLite (only if subcategory has no competitors yet)
    └─ [9] job.status = COMPLETED, job.resultJson = full response JSON
```

If any step throws, the job is marked `FAILED` in a separate transaction (so the failure persists even if the success transaction rolled back).

---

## Dynamic Metrics (The Core Product Rule)

Metrics are **never hardcoded**. The AI:

1. Classifies the product category + sub-category
2. Extracts recurring discussion themes from posts
3. Selects the **4 most purchase-relevant** themes as metrics
4. Scores each metric 0–10 based on community sentiment
5. Computes a **blended overall score** (sentiment balance + repetition strength + decision importance + confidence volume)
6. Crafts a single `verdictSentence`

**Examples:**
- Trimmer → Battery Life, Cutting Performance, Skin Comfort, Build Quality
- Speaker → Sound Quality, Battery Life, Loudness, Portability
- Running shoes → Comfort, Durability, Fit Accuracy, Value for Money

---

## AI Response Shape (SearchResponse)

```json
{
  "id": "uuid",
  "query": "Philips Trimmer",
  "productCategory": "Grooming",
  "productSubCategory": "Electric Trimmer",
  "overallScore": 82,
  "verdictSentence": "Strong daily-use trimmer with excellent battery, but build quality feels plasticky for the price.",
  "metrics": [
    { "label": "Battery Life", "score": 8.7, "explanation": "..." },
    { "label": "Cutting Performance", "score": 8.2, "explanation": "..." },
    { "label": "Skin Comfort", "score": 7.9, "explanation": "..." },
    { "label": "Build Quality", "score": 7.4, "explanation": "..." }
  ],
  "positives": ["Long battery praised by most users", "Easy to clean", "Good value"],
  "complaints": ["Weak on thick beard", "Plastic feels cheap"],
  "bestFor": ["Daily groomers", "Budget buyers"],
  "avoid": ["Heavy beard users", "Premium build seekers"],
  "evidenceSnippets": [
    { "text": "...", "source": "r/malegrooming", "permalink": "..." }
  ],
  "productImageUrl": "https://...",
  "productImageBase64": "data:image/jpeg;base64,...",
  "postCount": 20,
  "sourcePlatforms": ["reddit"],
  "analyzedAt": "...",
  "cached": false
}
```

---

## Cache Strategy

**Layer 1 — Exact (SHA-256 hash of normalized query)**
Fast O(1) DynamoDB lookup. Hit → HTTP 200 immediately.

**Layer 2 — Similar (Jaccard word-set similarity scan)**
Scanned when exact lookup misses. Threshold: configurable (default 0.75).

**Layer 3 — Cold**
HTTP 202 → RQueue job → full pipeline.

Cache TTL: configurable (default 24 hours). Stored only when AI succeeded (metrics list non-empty).

---

## Competitor Intelligence

After each successful analysis, `SearchJobListener` checks if any competitors exist for the detected subcategory in SQLite. If none do, it seeds AI-suggested competitors as placeholder rows (with estimated scores). Real user searches for those products replace the estimates with actual scores. The competitor query always picks `MAX(createdAt)` so real scores win.

---

## Rate Limiting & Circuit Breaking

| Guard | Scope | Config |
|---|---|---|
| Bucket4j token bucket | Reddit API (60 req/min) | `rate-limit.reddit-api` |
| Bucket4j token bucket | Reddit scraper (20 req/min) | `rate-limit.reddit-scraper` |
| Resilience4j circuit breaker | Reddit API | Opens at 50% failure rate |
| Resilience4j circuit breaker | OpenAI | Opens at 50%, 60s wait |
| Resilience4j circuit breaker | Gemini | Opens at 50%, 60s wait |

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **SQLite over PostgreSQL** | Zero ops overhead on 1 GB VM; WAL mode handles 1-writer concurrency fine |
| **Redis + RQueue over sync HTTP** | Reddit + AI pipeline takes 15–45s; async prevents request timeout |
| **DynamoDB for full AI JSON** | ~10 KB per result; TTL auto-expiry; no schema migrations needed |
| **concurrency=1 on RQueue listener** | 1 GB RAM can't safely run parallel AI + Redis + SQLite workloads |
| **No retry on listener (numRetries=0)** | Retrying a failed Reddit+AI job wastes quota; client retries via UI |
| **Image base64 encoding** | Share card renders CORS-safe without proxying the image URL |
| **Gemini for loading hints only** | Keeps OpenAI quota for high-value analysis; Gemini is free-tier safe |
| **Flyway disabled** | SQLite DDL via Hibernate `ddl-auto: update` is simpler for single-instance |

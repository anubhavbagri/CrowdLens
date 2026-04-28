# CrowdLens — Implementation Plan (Current State)

> This document reflects the **live, deployed system** as of April 2026.

---

## What Is CrowdLens

A product opinion analysis engine. Not a review site. Not a ranking page. It reads Reddit discussions and produces a structured, community-sourced verdict with **dynamically selected metrics** specific to the product category.

---

## Current Architecture Summary

```
Frontend (Vercel / Next.js 14)
    ↕  HTTP + polling
Backend (OCI Ubuntu VM / Docker)
    ├── Redis 7 — RQueue job queue
    ├── SQLite — job state + result index
    └── AWS DynamoDB — response cache (full AI JSON)
```

---

## Backend: Service Map

### Controllers

| Class | Endpoints | Description |
|---|---|---|
| `SearchController` | `POST /api/search`, `GET /api/search/{jobId}`, `GET /api/loading-hints` | Search submission, polling, loading hints |
| `HealthController` | `GET /api/health` | Liveness + AI connectivity check |
| `TrendingController` | `GET /api/trending` | Recent top queries from SQLite |
| `GlobalExceptionHandler` | — | Structured JSON error responses |

### Services

| Class | Responsibility |
|---|---|
| `SearchOrchestrator` | Cache lookup → persist job → enqueue to Redis → poll job status |
| `SearchJobListener` | RQueue worker: scrape → AI → image → SQLite → DynamoDB → competitors |
| `AIAnalysisEngine` | Sends prompt to OpenAI, parses dynamic JSON response |
| `PromptBuilder` | Builds the 9-step analysis prompt; instructs AI to select 4 dynamic metrics |
| `CacheService` | DynamoDB exact (SHA-256) + similar (Jaccard) cache lookup/put |
| `CompetitorService` | Retrieves top-scored competitors for same subcategory from SQLite |
| `ImageResolutionService` | Reddit AI-validated URL → Amazon ASIN fallback → base64 encode |
| `LoadingHintsService` | Gemini-generated loading messages (4–5 per query); degrades gracefully |
| `TrendingService` | Queries SQLite for recent high-scoring searches |
| `GeminiChatService` | Secondary AI client for lightweight tasks (loading hints) |

### Entities (SQLite)

| Entity | Key Fields |
|---|---|
| `SearchJob` | `id`, `query`, `queryNormalized`, `status (PENDING/IN_PROGRESS/COMPLETED/FAILED)`, `resultJson`, `errorMessage` |
| `SearchResult` | `id`, `query`, `queryNormalized`, `overallScore`, `productCategory`, `productSubCategory`, `verdictSentence`, `imageUrl`, `postCount`, `createdAt` |
| `SocialPost` | `id`, `platform`, `platformId`, `title`, `body`, `score`, `permalink`, `postedAt` |

### DTOs

| Class | Description |
|---|---|
| `SearchRequest` | `query` (required), `limit` (default 10), `maxComments` (default 50) |
| `SearchResponse` | Full verdict: `metrics[]`, `verdictSentence`, `positives[]`, `complaints[]`, `bestFor[]`, `avoid[]`, `evidenceSnippets[]`, `productImageUrl`, `productImageBase64` |
| `JobStatusResponse` | `jobId`, `status`, `result?`, `error?` |
| `CompetitorDto` | `name`, `score`, `productCategory`, `productSubCategory`, `imageUrl` |
| `TrendingResponse` | `query`, `score`, `category` |

---

## Async Pipeline Detail

### POST /api/search

```
1. Check DynamoDB cache (exact SHA-256 match)
   → HIT:  return HTTP 200 with full SearchResponse immediately

2. MISS:  persistJob() [@Transactional — commits before enqueue!]
          enqueueJob() [Redis → rqueue "search-jobs" queue]
          return HTTP 202 { jobId, status: "PENDING" }
```

### SearchJobListener (concurrency=1)

```
[1] platformRegistry.searchAll() — Reddit scrape
    ├─ 10 posts × 50 comments (configurable)
    └─ Deduplication + bot/deleted/AutoMod filter

[2] aiEngine.analyze() — OpenAI
    └─ PromptBuilder 9-step prompt:
       classify → extract themes → rank 4 metrics
       → score each → blended overall score
       → verdictSentence → positives/complaints
       → bestFor/avoid → evidenceSnippets
       → competitorSeeds (3 similar products + estimated scores)

[3] imageResolutionService.fetchFromAmazon() — fallback image
    └─ AI may return a Reddit image URL; if not, try Amazon

[4] toBase64DataUri() — CORS-safe for share card

[5] Save SearchResult to SQLite (lean row — index only)

[6] Save SocialPosts to SQLite (deduplicated by platformId)

[7] Build full SearchResponse → cache in DynamoDB
    └─ Only if metrics list is non-empty (AI succeeded)

[8] seedCompetitorsIfNeeded()
    └─ If no SearchResult rows exist for this subcategory,
       insert AI-estimated competitor placeholder rows

[9] job.status = COMPLETED, job.resultJson = full JSON
```

### GET /api/search/{jobId}

```
PENDING/IN_PROGRESS → { jobId, status }
COMPLETED           → { jobId, status, result: SearchResponse }
FAILED              → { jobId, status, error: "..." }
```

Frontend polls every 2 seconds, max 90 polls (3 minutes timeout).

---

## Frontend: Component Map

| Component | Role |
|---|---|
| `page.tsx` | State machine: idle → loading → results → error |
| `SearchBar` | Input with AI-hint autosuggest |
| `Navbar` | Appears after first search |
| `ShimmerSkeleton` | Loading skeleton with Gemini-powered contextual hints |
| `VerdictCard` | Score circle + category badge + verdictSentence + 4 inline metric bars |
| `MetricsGrid` | 2-col detailed metric cards with animated bars + Excellent/Good/Fair/Weak labels |
| `OpinionBlocks` | Positives/Complaints + BestFor/Avoid + Evidence snippets |
| `CompetitorCard` | Side-by-side competitor scores from SQLite |
| `ShareCard` | 480px exportable verdict PNG (html2canvas) — Download + Copy |
| `SharePrompt` | Share CTA with platform links |
| `TrendingSection` | Recent popular searches from `GET /api/trending` |
| `ResultsView` | Orchestrates all result components |

### api.ts: Client Flow

```typescript
// Cache hit → HTTP 200 → immediate result
// Cache miss → HTTP 202 → poll every 2s via GET /api/search/{jobId}
// Timeout after 90 polls (3 min)
export type JobStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
```

---

## Environment Variables

### Backend (.env / Docker)

| Variable | Description |
|---|---|
| `OPENAI_API_KEY` | OpenAI API key (primary AI) |
| `AI_MODEL` | OpenAI model name e.g. `gpt-4o` |
| `GEMINI_API_KEY` | Google Gemini key (loading hints) |
| `GEMINI_MODEL` | Gemini model name e.g. `gemini-2.0-flash` |
| `REDDIT_CLIENT_ID` | Reddit OAuth2 app client ID |
| `REDDIT_CLIENT_SECRET` | Reddit OAuth2 app client secret |
| `REDDIT_USERNAME` | Reddit account username |
| `REDDIT_PASSWORD` | Reddit account password |
| `REDDIT_USER_AGENT` | User-Agent string for Reddit API |
| `AWS_REGION` | AWS region for DynamoDB |
| `AWS_ACCESS_KEY_ID` | AWS IAM access key |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM secret key |
| `REDIS_HOST` | Redis host (set to `redis` in Docker Compose) |
| `REDIS_PORT` | Redis port (default `6379`) |
| `SHOW_SQL` | Set `true` to log Hibernate SQL (dev only) |

### Frontend (Vercel)

| Variable | Description |
|---|---|
| `NEXT_PUBLIC_API_URL` | Backend API base URL e.g. `https://crowdlens-api.anubhavbagri.com/api` |

---

## Deployment: Production

### Backend (OCI Ubuntu VM)

```bash
# .env file in project root with all env vars above
docker compose up --build -d

# Services started:
# - crowdlens-redis (Redis 7, no persistence)
# - crowdlens-backend (Spring Boot, waits for Redis healthy)
```

SQLite data persisted in Docker volume `sqlite_data` → `/app/data/crowdlens.db`.

### Frontend (Vercel)

- Root: `./frontend`
- Framework: Next.js (auto-detected)
- Env var: `NEXT_PUBLIC_API_URL = https://crowdlens-api.anubhavbagri.com/api`

### Domains

| Service | URL |
|---|---|
| Frontend | `https://crowdlens.anubhavbagri.com` |
| Backend API | `https://crowdlens-api.anubhavbagri.com` |
| Swagger UI | `https://crowdlens-api.anubhavbagri.com/swagger-ui.html` |

---

## What Was Deliberately Removed / Replaced

| Old | New | Reason |
|---|---|---|
| PostgreSQL | SQLite | Zero ops on 1 GB VM |
| Sync `executeSearch()` | Async `persistJob()` + RQueue | Reddit+AI is 15–45s; sync would timeout |
| Fixed categories (Efficacy/Quality/etc.) | Dynamic 4 metrics per product | Core product rule |
| `overallVerdict` (Excellent/Good/Mixed/Poor) | `verdictSentence` (crafted sentence) | More informative |
| `CategoryAnalysis`, `Testimonial`, `PersonaAnalysis` | `Metric`, `EvidenceSnippet`, `bestFor[]`, `avoid[]` | New response shape (Phase 5) |
| `ScrapeCursor` / incremental crawling | Simple per-job scrape | Simpler; reuse DynamoDB cache instead |

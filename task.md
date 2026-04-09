# CrowdLens — Tasks

## Phase 0: Planning
- [x] Research references (Matiks Monitor, tally.shop, Reddit API, hosting)
- [x] Architecture doc: design principles, patterns, HLD, LLD, DB design
- [x] Scraping intelligence strategy (incremental cursor, anti-ban)
- [x] Implementation plan (DynamoDB cache, local-first, Tailwind, Spring AI)
- [x] User approval ✅

## Phase 1: Backend Foundation (Local Docker) ✅
- [x] Spring Boot 3.2 project init (Java 17, Maven)
- [x] Docker Compose: PostgreSQL + DynamoDB Local + Spring Boot (multi-stage Dockerfile)
- [x] Reddit OAuth2 client (script-type, 4 credentials)
- [x] Reddit `.json` fallback scraper
- [x] Token bucket rate limiting (Bucket4j) + circuit breaker (Resilience4j)
- [x] `PlatformProvider` interface + `PlatformRegistry` + `RedditProvider`
- [x] `RedditDataAggregator` (dedup, filter bots/deleted/AutoMod)
- [x] Database schema (JPA entities + Flyway migrations)
- [x] Incremental cursor (`ScrapeCursor` entity + `ScrapeCursorService` + integrated in `RedditProvider`)

## Phase 2: AI Pipeline (Local) ✅
- [x] Spring AI integration (OpenAI, model-agnostic via `ChatModel`)
- [x] `PromptBuilder` with query-type auto-detection + dynamic categories
- [x] `CacheService` (DynamoDB-backed, TTL auto-expiry)
- [x] `SearchOrchestrator` (end-to-end pipeline)
- [x] REST endpoints: `POST /api/search`, `GET /api/health`
- [x] API documentation: SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`)
- [x] `GlobalExceptionHandler` (structured JSON error responses)

## Phase 3: Frontend (Minimal, Modular) ✅
- [x] Next.js 14 + TypeScript + Tailwind CSS v4
- [x] Search home page (centered search bar, tagline)
- [x] Analysis results page (score circle, verdict, categories, testimonials)
- [x] API client + loading/error states

## Phase 4: Cloud Deployment ✅
- [x] Provision Ubuntu VM (Oracle Cloud Infrastructure)
- [x] Deploy Spring Boot backend via Docker (SQLite for storage)
- [x] Vercel frontend deploy
- [x] Custom domains: `crowdlens-api.anubhavbagri.com` (backend) + `crowdlens.anubhavbagri.com` (frontend)
- [x] README with full setup guide

## Phase 5: Dynamic Metrics Redesign ✅
- [x] New `SearchResponse` shape: `Metric`, `EvidenceSnippet`, `verdictSentence`, `bestFor`, `avoid`
- [x] Complete `PromptBuilder` rewrite — AI classifies product, extracts themes, selects 4 dynamic metrics
- [x] Updated `AIAnalysisEngine` parser for new JSON shape
- [x] Updated `SearchOrchestrator` mapping
- [x] Flyway V2 migration: `overall_verdict` → `verdict_sentence`, added `product_category` column
- [x] Updated `SearchResult` entity fields
- [x] Updated frontend TypeScript types in `api.ts`
- [x] Update frontend UI components to render new shape (Phase 6)
- [x] Shareable verdict card component (Phase 6)

## Phase 6: Frontend UI Update & Shareable Card ✅
- [x] `VerdictCard.tsx` — shows verdictSentence + 4 inline metric bars + badges
- [x] `MetricsGrid.tsx` — detailed 2-col metric cards with animated bars + score labels
- [x] `OpinionBlocks.tsx` — positives/complaints + bestFor/avoid + evidence snippets
- [x] `ShareCard.tsx` — pixel-perfect 480px card; Download PNG + Copy to clipboard (html2canvas)
- [x] `ResultsView.tsx` — rewired to new component chain; old CategoryCard/TestimonialCard/PersonaSection retired



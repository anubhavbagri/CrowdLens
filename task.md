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

## Phase 3: Frontend (Minimal, Modular)
- [ ] Next.js 14 + TypeScript + Tailwind CSS v4
- [ ] Search home page (centered search bar, tagline)
- [ ] Analysis results page (score circle, verdict, categories, testimonials)
- [ ] API client + loading/error states

## Phase 4: Cloud Deployment
- [ ] AWS Lambda packaging (Spring Cloud Function)
- [ ] Supabase PostgreSQL setup
- [ ] DynamoDB production table
- [ ] Vercel frontend deploy
- [ ] README with full setup guide

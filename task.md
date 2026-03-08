# CrowdLens — Tasks

## Phase 0: Planning
- [x] Research references (Matiks Monitor, tally.shop, Reddit API, hosting)
- [x] Architecture doc: design principles, patterns, HLD, LLD, DB design
- [x] Scraping intelligence strategy (incremental cursor, anti-ban)
- [x] Implementation plan (DynamoDB cache, local-first, Tailwind, Spring AI)
- [x] User approval ✅

## Phase 1: Backend Foundation (Local Docker)
- [ ] Spring Boot 3.2 project init (Java 21, Maven)
- [ ] Docker Compose: PostgreSQL + DynamoDB Local + Spring Boot
- [ ] Reddit OAuth2 client (script-type, 4 credentials)
- [ ] Reddit `.json` fallback scraper
- [ ] Token bucket rate limiting (Bucket4j) + circuit breaker (Resilience4j)
- [ ] `PlatformProvider` interface + `PlatformRegistry` + `RedditProvider`
- [ ] `RedditDataAggregator` (dedup, filter bots/deleted/AutoMod)
- [ ] Incremental cursor (`ScrapeCursor` entity)
- [ ] Database schema (JPA entities + Flyway migrations)

## Phase 2: AI Pipeline (Local)
- [ ] Spring AI integration (OpenAI, model-agnostic via `ChatModel`)
- [ ] `PromptBuilder` with query-type auto-detection + dynamic categories
- [ ] `CacheService` (DynamoDB-backed, TTL auto-expiry)
- [ ] `SearchOrchestrator` (end-to-end pipeline)
- [ ] REST endpoint: `POST /api/search`, `GET /api/health`

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

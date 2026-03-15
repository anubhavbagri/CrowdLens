# CrowdLens

> Aggregates authentic user opinions from social media platforms and uses AI for structured analysis.

Search any product, service, or experience — CrowdLens scrapes Reddit for real opinions, analyzes them with AI, and returns a structured verdict with scores, categories, testimonials, and persona matching.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.2, Maven |
| **AI** | Spring AI (model-agnostic: OpenAI, Anthropic, Gemini, Ollama) |
| **Database** | SQLite (Embedded) |
| **Cache** | DynamoDB (TTL-based key-value) |
| **Rate Limiting** | Bucket4j (token bucket) |
| **Resilience** | Resilience4j (circuit breaker) |
| **API Docs** | SpringDoc OpenAPI (Swagger UI) |
| **Local Dev** | Docker Compose |
| **Frontend** | Next.js 14, TypeScript, Tailwind CSS v4 |

---

## Backend

### Architecture

Layered/N-Tier architecture with pluggable platform providers:

```
Controller → Orchestrator → AI Engine + Platform Providers → Repositories
                ↕                        ↕
           CacheService            Reddit (API + Scraper)
```

### Design Patterns

| Pattern | Implementation |
|---------|---------------|
| **Strategy** | `PlatformProvider` interface — Reddit now, Twitter/HN pluggable later |
| **Chain of Responsibility** | `RedditProvider`: OAuth2 API → JSON scraper fallback |
| **Circuit Breaker** | Resilience4j on Reddit API + OpenAI calls |
| **Token Bucket** | Bucket4j: 60 req/min (API), 20 req/min (scraper) |
| **Incremental Cursor** | `ScrapeCursorService`: skips already-seen posts/comments on repeat queries |

### Project Structure

```
backend/
├── src/main/java/com/crowdlens/
│   ├── CrowdLensApplication.java
│   ├── config/
│   │   ├── AppConfig.java              # WebClient beans, DynamoDB client
│   │   ├── OpenApiConfig.java          # Swagger UI config
│   │   ├── RedditProperties.java
│   │   ├── DynamoDbProperties.java
│   │   └── RateLimitProperties.java
│   ├── controller/
│   │   ├── SearchController.java       # POST /api/search
│   │   ├── HealthController.java       # GET /api/health
│   │   └── GlobalExceptionHandler.java
│   ├── service/
│   │   ├── SearchOrchestrator.java     # Full pipeline: cache → search → AI → persist
│   │   ├── AIAnalysisEngine.java       # Spring AI ChatModel + JSON parsing
│   │   ├── PromptBuilder.java          # Query-type detection + dynamic categories
│   │   ├── CacheService.java           # DynamoDB-backed with SHA-256 keys + TTL
│   │   └── ScrapeCursorService.java    # Incremental cursor for deduplication
│   ├── provider/
│   │   ├── PlatformProvider.java       # Strategy interface
│   │   ├── PlatformRegistry.java       # Auto-discovers providers
│   │   └── reddit/
│   │       ├── RedditProvider.java     # Chain: API → scraper → comments → aggregate
│   │       ├── RedditApiClient.java    # OAuth2 + circuit breaker
│   │       ├── RedditJsonScraper.java  # Stealth fallback (UA rotation, jitter)
│   │       ├── RedditDataAggregator.java
│   │       └── RedditRateLimiter.java  # Dual Bucket4j buckets
│   ├── model/
│   │   ├── dto/                        # SearchRequest, SearchResponse, SocialPostDto
│   │   └── entity/                     # SearchResult, SocialPost, ScrapeCursor (JPA)
│   └── repository/                     # Spring Data JPA repos
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V1__init_schema.sql
├── pom.xml
└── Dockerfile                          # Multi-stage build (Maven + JRE)
```

### Prerequisites

- **Docker Desktop** — [Install](https://docs.docker.com/desktop/)
- **Reddit API credentials** — Create a "script" app at [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps)
- **OpenAI API key** — Get one at [platform.openai.com/api-keys](https://platform.openai.com/api-keys)

### Setup & Run Locally

```bash
# 1. Clone the repo
git clone https://github.com/anubhavbagri/CrowdLens.git
cd CrowdLens

# 2. Create .env from template and fill in your credentials
cp .env.example .env
# Edit .env with your Reddit + OpenAI credentials

# 3. Start all services (PostgreSQL + DynamoDB Local + Backend)
docker compose up --build

# 4. Verify
curl http://localhost:8080/api/health
```

The first build takes ~3-5 minutes (downloading Maven dependencies). Subsequent builds are fast due to Docker layer caching.

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/search` | Analyze crowd opinions for a query |
| `GET` | `/api/health` | Service health + Reddit/AI connectivity |

**Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

#### Search Request

```json
POST /api/search
{
  "query": "creatine supplement",
  "limit": 10,         // optional, default: 10, max: 50
  "maxComments": 50    // optional, default: 50, max: 100
}
```

#### Search Response

```json
{
  "id": "uuid",
  "query": "creatine supplement",
  "overallScore": 82,
  "overallVerdict": "Highly Recommended",
  "verdictSummary": "...",
  "categories": [
    { "name": "Effectiveness", "rating": "Positive", "summary": "...", "highlights": [...] }
  ],
  "testimonials": [
    { "text": "...", "sentiment": "positive", "source": "r/supplements", "permalink": "..." }
  ],
  "personaAnalysis": {
    "question": "Is this right for you?",
    "fits": [
      { "persona": "Gym Regular", "verdict": "Great fit", "reason": "..." }
    ]
  },
  "postCount": 20,
  "sourcePlatforms": ["reddit"],
  "analyzedAt": "2026-03-09T...",
  "cached": false
}
```

### Querying Databases Locally

**SQLite:**
```bash
# SQLite DB is stored locally in the mounted /data volume or backend directory
sqlite3 backend/data/crowdlens.db
```

**DynamoDB Local:**
```bash
aws dynamodb scan --table-name crowdlens-cache \
  --endpoint-url http://localhost:8000 --region us-east-1
```

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `REDDIT_CLIENT_ID` | ✅ | — | Reddit app client ID |
| `REDDIT_CLIENT_SECRET` | ✅ | — | Reddit app secret |
| `REDDIT_USERNAME` | ✅ | — | Reddit account username |
| `REDDIT_PASSWORD` | ✅ | — | Reddit account password |
| `OPENAI_API_KEY` | ✅ | — | OpenAI API key |
| `AI_MODEL` | | `gpt-4o-mini` | AI model to use |
| `DYNAMODB_ENDPOINT` | | `http://localhost:8000` | DynamoDB endpoint (use AWS URL in prod) |
| `AWS_ACCESS_KEY_ID` | | — | AWS IAM Access Key for DynamoDB |
| `AWS_SECRET_ACCESS_KEY` | | — | AWS IAM Secret Key for DynamoDB |
| `AWS_REGION` | | `us-east-1` | AWS Region for DynamoDB |
| `CACHE_TTL_HOURS` | | `24` | Cache expiry in hours |

---

## Frontend

Built with Next.js 14, TypeScript, and Tailwind CSS v4. Features a tally.shop-inspired clean UI with micro-animations, glassmorphism, and a seamless single-page search experience.

---

## License

MIT

# CrowdLens

**Community-sourced product intelligence.** CrowdLens reads Reddit so you don't have to — turning thousands of real opinions into a structured, shareable product verdict with dynamically selected metrics.

Live: [crowdlens.anubhavbagri.com](https://crowdlens.anubhavbagri.com)

---

## What It Does

Search any product. CrowdLens:

1. Scrapes Reddit (OAuth2 + fallback scraper) for relevant posts and comments
2. Classifies the product category and sub-category
3. Extracts the top discussion themes — **4 dynamic metrics selected per product** (not fixed templates)
4. Scores each metric 0–10 based on community sentiment
5. Produces a blended overall score, a crafted one-sentence verdict, positives/complaints, and persona fits
6. Suggests similar competing products with estimated scores

Results are returned as a shareable card image (PNG download / copy to clipboard).

---

## Architecture & Technical Flow

CrowdLens is structured as a **Layered (N-Tier) Architecture** to ensure clean separation of concerns, scalability under resource constraints, and fault tolerance.

```
+------------------------------------------------------------------------+
|                      Presentation Layer (Controllers)                  |
|          SearchController | TrendingController | HealthController      |
+------------------------------------+-----------------------------------+
                                     |
                                     v
+------------------------------------------------------------------------+
|                         Orchestration Layer                            |
|                          SearchOrchestrator                            |
+-------------------+----------------+-----------------------------------+
                    |                |
                    v (Cache Hit)    v (Cache Miss)
+-----------------------+        +---------------------------------------+
|  Cache Layer (NoSQL)  |        |      Primary DB Layer (Relational)    |
|     CacheService      |        |   SearchJobRepository (PENDING job)   |
|    (AWS DynamoDB)     |        +-------------------+-------------------+
+-----------------------+                            |
                                                     v
                                 +---------------------------------------+
                                 |            Redis Job Queue            |
                                 |          RqueueMessageEnqueuer        |
                                 +-------------------+-------------------+
                                                     |
                                                     v (Async Dequeue)
+----------------------------------------------------+-------------------+
|                     Asynchronous Execution Layer                       |
|                          SearchJobListener                             |
|    - PlatformRegistry (Crawlers)     - AIAnalysisEngine (OpenAI)       |
|    - ImageResolutionService (Images) - CompetitorService (Seeding)     |
+-------------------+--------------------------------+-------------------+
                    |                                |
                    v                                v
+-----------------------+        +---------------------------------------+
|  Cache Layer (NoSQL)  |        |      Primary DB Layer (Relational)    |
|   AWS DynamoDB cache  |        |   SQLite (SearchResult/SocialPost)    |
+-----------------------+        +---------------------------------------+
```

### End-to-End Execution Flow

#### 1. Presentation Layer (Request Receipt)
1. The client submits a search query via `POST /api/search` to [SearchController](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/controller/SearchController.java).
2. The Controller delegates the cache check to the Orchestration Layer ([SearchOrchestrator](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/service/SearchOrchestrator.java)).

#### 2. Orchestration & Fast Path (Cache Hit)
1. `SearchOrchestrator` queries [CacheService](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/service/CacheService.java) using the normalized query SHA-256 hash.
2. `CacheService` does an $O(1)$ query to AWS DynamoDB. 
   - **Cache Hit:** The serialized JSON is retrieved, validated against its `expires_at` timestamp, deserialized into a `SearchResponse`, enriched with competitors via [CompetitorService](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/service/CompetitorService.java), and returned to the client immediately with **HTTP 200 OK** in `< 100ms`.
   - **Cache Miss:** The Orchestrator initiates the slow path.

#### 3. Slow Path (Job Ingestion & Decoupling)
1. `SearchOrchestrator` creates a new `SearchJob` record in `PENDING` state and persists it to the local SQLite database.
2. Once the SQLite transaction successfully commits, the Orchestrator publishes a `SearchJobMessage` containing the `jobId` to the Redis queue via `RqueueMessageEnqueuer`.
3. The Controller immediately returns **HTTP 202 Accepted** to the client, along with the `jobId` and `status: PENDING`. The HTTP thread is released.

#### 4. Asynchronous Execution Layer (Worker Pipeline)
1. [SearchJobListener](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/service/SearchJobListener.java) (running in the background with `concurrency=1` to optimize VPS resource footprint) dequeues the message.
2. The listener marks the job status as `IN_PROGRESS` in SQLite.
3. **Data Fetching:** It delegates crawling to [PlatformRegistry](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/provider/PlatformRegistry.java), which invokes [RedditProvider](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/provider/reddit/RedditProvider.java). It calls the official Reddit API ([RedditApiClient](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/provider/reddit/RedditApiClient.java)) and falls back to [RedditJsonScraper](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/provider/reddit/RedditJsonScraper.java) if rate-limited.
4. **AI Processing:** Scraped posts are sent to [AIAnalysisEngine](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/service/AIAnalysisEngine.java) (powered by Spring AI's `ChatModel` interfacing with OpenAI). The AI classifies categories, evaluates sentiment scores, and constructs a structured analysis response.
5. **Image Resolution:** [ImageResolutionService](file:///d:/indie%20side%20projects/CrowdLens/backend/src/main/java/com/crowdlens/service/ImageResolutionService.java) fetches a relevant Amazon product image if no Reddit image is resolved, and converts it to a CORS-safe base64 data URI.
6. **Data Persistence (SQLite & Cache):** Using a transactional wrapper (`TransactionTemplate`):
   - A lean result index is saved into the SQLite `search_results` table.
   - Collected social posts are deduplicated and saved into the SQLite `social_posts` table.
   - The full JSON analysis payload is cached in AWS DynamoDB with a TTL.
   - Competitor placeholder rows are seeded in SQLite if none exist for the subcategory.
   - The job row in SQLite is marked `COMPLETED` and the serialized response is stored on the job record (`result_json`).

#### 5. Client Polling Flow
1. The client polls `GET /api/search/{jobId}` every 2 seconds.
2. The Controller queries the Orchestrator, which checks the SQLite job status.
3. While the status is `PENDING` or `IN_PROGRESS`, the server returns a lightweight status response.
4. Once marked `COMPLETED`, the server retrieves the `result_json` from the SQLite job row, deserializes it, and returns the full analysis payload with **HTTP 200 OK**, concluding the flow.

See [architecture.md](./architecture.md) for full system specifications.

---

## Tech Stack

### Backend
- Java 17 / Spring Boot 3.2
- Spring AI (model-agnostic: OpenAI primary, Gemini secondary)
- Redis 7 + RQueue (async job queue)
- SQLite (WAL mode) via Hibernate
- AWS DynamoDB (response cache)
- Resilience4j (circuit breakers) + Bucket4j (rate limiting)
- SpringDoc OpenAPI (Swagger UI)
- Docker + Docker Compose

### Frontend
- Next.js 14 / TypeScript
- Tailwind CSS v4
- `html2canvas` (share card PNG export)
- Vercel (hosting)

---

## Local Development

### Prerequisites

- Java 17+, Maven 3.9+
- Docker Desktop
- Node.js 18+
- Reddit OAuth2 app credentials  
- OpenAI API key  
- Gemini API key (optional — loading hints degrade gracefully without it)
- AWS credentials for DynamoDB (or use local DynamoDB for dev)

### 1. Clone

```bash
git clone https://github.com/anubhavbagri/CrowdLens.git
cd CrowdLens
```

### 2. Backend environment

Create `.env` in the project root:

```env
# OpenAI (primary AI — product analysis)
OPENAI_API_KEY=sk-...
AI_MODEL=gpt-4o

# Gemini (secondary AI — loading hints only)
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-2.0-flash

# Reddit OAuth2
REDDIT_CLIENT_ID=...
REDDIT_CLIENT_SECRET=...
REDDIT_USERNAME=...
REDDIT_PASSWORD=...
REDDIT_USER_AGENT=CrowdLens/1.0

# AWS DynamoDB
AWS_REGION=ap-south-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...

# Redis (set automatically by docker-compose — only needed for external Redis)
REDIS_HOST=localhost
REDIS_PORT=6379

# Optional
SHOW_SQL=false
```

### 3. Start with Docker Compose

```bash
docker compose up --build
```

This starts:
- `crowdlens-redis` (Redis 7, in-memory, no persistence)
- `crowdlens-backend` (Spring Boot on port 8080, waits for Redis)

SQLite database is stored in a Docker volume at `/app/data/crowdlens.db`.

### 4. Verify backend

```
GET  http://localhost:8080/api/health
     http://localhost:8080/swagger-ui.html
```

### 5. Frontend

```bash
cd frontend
npm install
cp .env.local.example .env.local
# Set NEXT_PUBLIC_API_URL=http://localhost:8080/api
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

---

## API Reference

### POST `/api/search`

Submits a search. Returns immediately — either a cached result (HTTP 200) or a job receipt (HTTP 202).

```json
// Request
{
  "query": "JBL Flip 6",
  "limit": 10,
  "maxComments": 50
}

// Cache HIT → HTTP 200: full SearchResponse (see below)

// Cache MISS → HTTP 202:
{ "jobId": "uuid", "status": "PENDING" }
```

### GET `/api/search/{jobId}`

Poll this every 2 seconds until status changes.

```json
// Still running:
{ "jobId": "...", "status": "IN_PROGRESS" }

// Done:
{ "jobId": "...", "status": "COMPLETED", "result": { /* SearchResponse */ } }

// Failed:
{ "jobId": "...", "status": "FAILED", "error": "..." }
```

### SearchResponse shape

```json
{
  "id": "uuid",
  "query": "JBL Flip 6",
  "productCategory": "Audio",
  "productSubCategory": "Bluetooth Speaker",
  "overallScore": 84,
  "verdictSentence": "Excellent portable speaker with punchy sound and great battery, though bass-heavy tuning won't suit everyone.",
  "metrics": [
    { "label": "Sound Quality", "score": 8.8, "explanation": "..." },
    { "label": "Battery Life",  "score": 8.5, "explanation": "..." },
    { "label": "Portability",   "score": 9.1, "explanation": "..." },
    { "label": "Loudness",      "score": 8.0, "explanation": "..." }
  ],
  "positives": ["Punchy bass", "IP67 waterproof", "Compact size"],
  "complaints": ["No EQ control", "Slightly overpriced"],
  "bestFor": ["Outdoor use", "Casual listeners", "Travel"],
  "avoid": ["Audiophiles wanting flat response"],
  "evidenceSnippets": [
    { "text": "...", "source": "r/audiophile", "permalink": "https://..." }
  ],
  "productImageUrl": "https://...",
  "productImageBase64": "data:image/jpeg;base64,...",
  "postCount": 20,
  "sourcePlatforms": ["reddit"],
  "analyzedAt": "2026-04-26T12:00:00Z",
  "cached": false
}
```

### GET `/api/loading-hints?q={query}`

Returns 4–5 Gemini-generated loading messages for the query. Returns empty list if Gemini is unavailable.

### GET `/api/trending`

Returns recent high-scoring searches from SQLite.

### GET `/api/health`

Returns backend health status + AI connectivity check.

---

## Production Deployment

### Backend (OCI Ubuntu VM)

```bash
# SSH into VM
cd ~/CrowdLens

# Pull latest
git pull origin main

# Rebuild and restart
docker compose up --build -d

# Check logs
docker compose logs -f backend
docker compose logs -f redis
```

**Required on server:** `.env` file with all credentials listed in the Local Development section above.

SQLite data persists in Docker volume `sqlite_data` — survives container restarts and rebuilds.

### Frontend (Vercel)

1. Connect GitHub repo to Vercel
2. Set root directory: `frontend`
3. Add environment variable: `NEXT_PUBLIC_API_URL = https://crowdlens-api.anubhavbagri.com/api`
4. Deploy — Vercel auto-deploys on every `main` push

### Custom Domains

| Service | Domain | DNS |
|---|---|---|
| Frontend | `crowdlens.anubhavbagri.com` | CNAME → `cname.vercel-dns.com` |
| Backend | `crowdlens-api.anubhavbagri.com` | A record → OCI VM IP |

---

## Project Structure

```
CrowdLens/
├── backend/
│   ├── src/main/java/com/crowdlens/
│   │   ├── controller/         # REST endpoints
│   │   ├── service/            # Business logic + AI + queue listener
│   │   ├── model/dto/          # Request/response shapes
│   │   ├── model/entity/       # JPA entities (SQLite)
│   │   ├── provider/           # Reddit scraper
│   │   ├── config/             # Spring config beans
│   │   └── util/               # JaccardUtils, helpers
│   ├── src/main/resources/
│   │   └── application.yml
│   └── Dockerfile
├── frontend/
│   └── src/
│       ├── app/                # Next.js pages
│       ├── components/         # UI components
│       └── lib/api.ts          # API client + polling logic
├── docker-compose.yml          # Redis + backend
├── architecture.md
├── implementation_plan.md
└── README.md
```

---

## Key Design Rules

- **Metrics are never hardcoded.** The AI selects 4 metrics per product based on what people actually discuss.
- **One job at a time.** `concurrency=1` on the RQueue listener — safe for 1 GB server.
- **Full AI JSON never touches SQLite.** Only DynamoDB stores the rich response; SQLite holds lightweight index rows.
- **Cache miss ≠ slow user experience.** HTTP 202 + polling means the browser stays responsive while the job runs in the background.
- **AI failure is graceful.** If OpenAI fails, the job is marked `FAILED`, the client shows a retry button, and nothing is cached.

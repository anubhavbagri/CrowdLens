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

## Architecture

```
Next.js (Vercel)  ──►  Spring Boot (OCI VM)  ──►  Redis (RQueue)
                               │                       │
                           SQLite                SearchJobListener
                               │                    │
                           DynamoDB ◄───── full AI JSON cached here
```

- **Redis + RQueue** — async job queue. Analysis takes 15–45 seconds; the HTTP request returns immediately with a `jobId` (HTTP 202). The frontend polls every 2 seconds until `COMPLETED`.
- **SQLite** — job state tracking + lean result index (query, score, category, image URL)
- **DynamoDB** — full AI JSON response cached with TTL (default 24h). Cache hit → HTTP 200 with instant result.
- **OpenAI** — primary AI for dynamic metric extraction and verdict generation
- **Google Gemini** — secondary AI for contextual loading hints only
- **`concurrency=1`** — one job runs at a time (intentional; 1 GB VM)

See [architecture.md](./architecture.md) for full detail.

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

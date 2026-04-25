# Graph Report - .  (2026-04-20)

## Corpus Check
- Corpus is ~31,432 words - fits in a single context window. You may not need a graph.

## Summary
- 271 nodes · 354 edges · 51 communities detected
- Extraction: 72% EXTRACTED · 28% INFERRED · 0% AMBIGUOUS · INFERRED: 99 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]

## God Nodes (most connected - your core abstractions)
1. `GET()` - 17 edges
2. `AIAnalysisEngine` - 12 edges
3. `error()` - 12 edges
4. `CacheService` - 10 edges
5. `RedditApiClient` - 8 edges
6. `RedditProvider` - 8 edges
7. `SearchOrchestrator` - 8 edges
8. `RedditDataAggregator` - 7 edges
9. `SearchResultRepository` - 7 edges
10. `empty()` - 7 edges

## Surprising Connections (you probably didn't know these)
- `Social Interaction Icon Motif` --rationale_for--> `Platform-Agnostic Product Vision`  [INFERRED]
  frontend/public/pattern-grey-wide.png → implementation_plan.md
- `Default Next.js Scaffold Guidance` --semantically_similar_to--> `Frontend Product Experience Positioning`  [INFERRED] [semantically similar]
  frontend/README.md → README.md
- `Frontend Product Experience Positioning` --conceptually_related_to--> `Social Interaction Icon Motif`  [INFERRED]
  README.md → frontend/public/pattern-grey-wide.png
- `Phased Delivery Plan` --rationale_for--> `Search Orchestrator Flow`  [INFERRED]
  implementation_plan.md → architecture.md
- `handleSearch()` --calls--> `searchCrowdLens()`  [INFERRED]
  frontend\src\app\page.tsx → frontend\src\lib\api.ts

## Hyperedges (group relationships)
- **Search Analysis Execution Pipeline** — architecture_search_orchestrator_flow, architecture_platform_provider_strategy, architecture_ai_provider_abstraction, architecture_dynamodb_ttl_cache, architecture_sqlite_primary_store [EXTRACTED 0.94]
- **Anti-Ban and Resilience Bundle** — architecture_reddit_fallback_chain, architecture_rate_limit_resilience, architecture_incremental_cursor_strategy, architecture_dynamodb_ttl_cache [EXTRACTED 0.91]
- **Frontend Visual Identity Cluster** — readme_frontend_product_experience, pattern-grey-wide_visual_artifact, pattern-grey-wide_social_interaction_motif, pattern-grey-wide_neutral_ui_texture [INFERRED 0.79]

## Communities

### Community 0 - "Community 0"
Cohesion: 0.13
Nodes (5): CompetitorService, CategoryProjection, SearchResultRepository, TrendingItemProjection, TrendingService

### Community 1 - "Community 1"
Cohesion: 0.14
Nodes (4): RedditApiClient, RedditJsonScraper, RedditRateLimiter, GET()

### Community 2 - "Community 2"
Cohesion: 0.12
Nodes (21): AI Provider Abstraction, Architecture Document, DynamoDB TTL Cache Layer, Incremental Cursor Dedup Strategy, Platform Provider Strategy, Rate Limiting and Resilience Controls, Reddit API-to-Scraper Fallback Chain, Search Orchestrator Flow (+13 more)

### Community 3 - "Community 3"
Cohesion: 0.13
Nodes (4): PlatformRegistry, SearchJobListener, SearchResult, SocialPostRepository

### Community 4 - "Community 4"
Cohesion: 0.15
Nodes (4): empty(), CacheService, ImageResolutionService, JaccardUtils

### Community 5 - "Community 5"
Cohesion: 0.18
Nodes (2): RedditDataAggregator, RedditProvider

### Community 6 - "Community 6"
Cohesion: 0.19
Nodes (4): SearchController, SearchOrchestrator, effectiveLimit(), effectiveMaxComments()

### Community 7 - "Community 7"
Cohesion: 0.29
Nodes (2): AIAnalysisEngine, error()

### Community 8 - "Community 8"
Cohesion: 0.25
Nodes (2): GlobalExceptionHandler, HealthController

### Community 9 - "Community 9"
Cohesion: 0.22
Nodes (2): GeminiChatService, LoadingHintsService

### Community 10 - "Community 10"
Cohesion: 0.38
Nodes (4): pollJob(), searchCrowdLens(), sleep(), handleSearch()

### Community 11 - "Community 11"
Cohesion: 0.33
Nodes (1): AppConfig

### Community 12 - "Community 12"
Cohesion: 0.53
Nodes (1): PromptBuilder

### Community 13 - "Community 13"
Cohesion: 0.4
Nodes (1): PlatformProvider

### Community 14 - "Community 14"
Cohesion: 0.4
Nodes (0): 

### Community 15 - "Community 15"
Cohesion: 0.5
Nodes (1): SearchJob

### Community 16 - "Community 16"
Cohesion: 0.5
Nodes (0): 

### Community 17 - "Community 17"
Cohesion: 0.67
Nodes (1): CrowdLensApplication

### Community 18 - "Community 18"
Cohesion: 0.67
Nodes (1): OpenAiRestClientConfig

### Community 19 - "Community 19"
Cohesion: 0.67
Nodes (1): OpenApiConfig

### Community 20 - "Community 20"
Cohesion: 0.67
Nodes (1): RedisConfig

### Community 21 - "Community 21"
Cohesion: 0.67
Nodes (1): TrendingController

### Community 22 - "Community 22"
Cohesion: 0.67
Nodes (1): SocialPost

### Community 23 - "Community 23"
Cohesion: 0.67
Nodes (1): SearchJobRepository

### Community 24 - "Community 24"
Cohesion: 0.67
Nodes (0): 

### Community 25 - "Community 25"
Cohesion: 1.0
Nodes (0): 

### Community 26 - "Community 26"
Cohesion: 1.0
Nodes (0): 

### Community 27 - "Community 27"
Cohesion: 1.0
Nodes (0): 

### Community 28 - "Community 28"
Cohesion: 1.0
Nodes (0): 

### Community 29 - "Community 29"
Cohesion: 1.0
Nodes (0): 

### Community 30 - "Community 30"
Cohesion: 1.0
Nodes (0): 

### Community 31 - "Community 31"
Cohesion: 1.0
Nodes (0): 

### Community 32 - "Community 32"
Cohesion: 1.0
Nodes (0): 

### Community 33 - "Community 33"
Cohesion: 1.0
Nodes (0): 

### Community 34 - "Community 34"
Cohesion: 1.0
Nodes (0): 

### Community 35 - "Community 35"
Cohesion: 1.0
Nodes (0): 

### Community 36 - "Community 36"
Cohesion: 1.0
Nodes (0): 

### Community 37 - "Community 37"
Cohesion: 1.0
Nodes (0): 

### Community 38 - "Community 38"
Cohesion: 1.0
Nodes (0): 

### Community 39 - "Community 39"
Cohesion: 1.0
Nodes (0): 

### Community 40 - "Community 40"
Cohesion: 1.0
Nodes (0): 

### Community 41 - "Community 41"
Cohesion: 1.0
Nodes (0): 

### Community 42 - "Community 42"
Cohesion: 1.0
Nodes (0): 

### Community 43 - "Community 43"
Cohesion: 1.0
Nodes (0): 

### Community 44 - "Community 44"
Cohesion: 1.0
Nodes (0): 

### Community 45 - "Community 45"
Cohesion: 1.0
Nodes (0): 

### Community 46 - "Community 46"
Cohesion: 1.0
Nodes (0): 

### Community 47 - "Community 47"
Cohesion: 1.0
Nodes (0): 

### Community 48 - "Community 48"
Cohesion: 1.0
Nodes (0): 

### Community 49 - "Community 49"
Cohesion: 1.0
Nodes (0): 

### Community 50 - "Community 50"
Cohesion: 1.0
Nodes (1): Layered N-Tier Architecture

## Knowledge Gaps
- **6 isolated node(s):** `Frontend README`, `Layered N-Tier Architecture`, `Rate Limiting and Resilience Controls`, `Phased Delivery Plan`, `Default Next.js Scaffold Guidance` (+1 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 25`** (2 nodes): `layout.tsx`, `RootLayout()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 26`** (2 nodes): `Navbar.tsx`, `Navbar()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 27`** (2 nodes): `ScoreCircle.tsx`, `ScoreCircle()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 28`** (2 nodes): `ShareCard.tsx`, `fallbackImageUrl()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 29`** (2 nodes): `SharePrompt.tsx`, `SharePrompt()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 30`** (2 nodes): `TrendingSection.tsx`, `handler()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 31`** (2 nodes): `VerdictCard.tsx`, `fallbackImageUrl()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 32`** (2 nodes): `useSearchSuggestions.ts`, `useSearchSuggestions()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 33`** (2 nodes): `useShareCard.ts`, `useShareCard()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 34`** (1 nodes): `DynamoDbProperties.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 35`** (1 nodes): `RateLimitProperties.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 36`** (1 nodes): `RedditProperties.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 37`** (1 nodes): `CompetitorDto.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 38`** (1 nodes): `JobStatusResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `SearchResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (1 nodes): `SocialPostDto.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 41`** (1 nodes): `TrendingResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (1 nodes): `SearchJobMessage.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `eslint.config.mjs`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (1 nodes): `next-env.d.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 45`** (1 nodes): `next.config.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 46`** (1 nodes): `postcss.config.mjs`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (1 nodes): `OpinionBlocks.tsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (1 nodes): `ResultsView.tsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (1 nodes): `ShimmerSkeleton.tsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (1 nodes): `Layered N-Tier Architecture`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GET()` connect `Community 1` to `Community 4`, `Community 5`, `Community 6`, `Community 7`, `Community 12`?**
  _High betweenness centrality (0.103) - this node is a cross-community bridge._
- **Why does `error()` connect `Community 7` to `Community 1`, `Community 3`, `Community 5`, `Community 6`, `Community 8`?**
  _High betweenness centrality (0.086) - this node is a cross-community bridge._
- **Why does `sleep()` connect `Community 10` to `Community 1`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **Are the 16 inferred relationships involving `GET()` (e.g. with `.search()` and `.getJobStatus()`) actually correct?**
  _`GET()` has 16 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `error()` (e.g. with `.handleGeneral()` and `.getJobStatus()`) actually correct?**
  _`error()` has 7 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Frontend README`, `Layered N-Tier Architecture`, `Rate Limiting and Resilience Controls` to the rest of the system?**
  _6 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.13 - nodes in this community are weakly interconnected._
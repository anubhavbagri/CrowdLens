export interface CategoryAnalysis {
  name: string;
  rating: string;
  summary: string;
  highlights: string[];
}

export interface Testimonial {
  text: string;
  sentiment: string;
  source: string;
  platform: string;
  permalink: string;
}

export interface PersonaFit {
  persona: string;
  verdict: string;
  reason: string;
}

export interface PersonaAnalysis {
  question: string;
  fits: PersonaFit[];
}

export interface SearchResponse {
  id: string;
  query: string;
  overallScore: number;
  overallVerdict: string;
  verdictSummary: string;
  categories: CategoryAnalysis[];
  testimonials: Testimonial[];
  personaAnalysis: PersonaAnalysis;
  postCount: number;
  sourcePlatforms: string[];
  analyzedAt: string;
  cached: boolean;
}

export interface SearchRequest {
  query: string;
  limit?: number;
  maxComments?: number;
}

export type JobStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

export interface JobResponse {
  jobId: string;
  status: JobStatus;
  result?: SearchResponse;
  error?: string;
}

// Callback invoked during polling so the UI can reflect the current job status.
export type StatusCallback = (status: JobStatus) => void;

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const POLL_INTERVAL_MS = 2_000;
const POLL_MAX_ATTEMPTS = 60; // 60 × 2s = 2 minutes

/**
 * Submit a search and resolve to the full SearchResponse.
 *
 * - HTTP 200 (cache hit): returns immediately.
 * - HTTP 202 (job queued): polls GET /api/search/{jobId} every 2s until
 *   COMPLETED or FAILED, or until the 2-minute timeout is reached.
 *
 * @param request    The search payload.
 * @param onStatus   Optional callback called with each polled status so the UI
 *                   can show "Queued…" vs "Analyzing…" labels.
 */
export async function searchCrowdLens(
  request: SearchRequest,
  onStatus?: StatusCallback,
): Promise<SearchResponse> {
  const response = await fetch(`${API_BASE_URL}/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  // Cache hit — return the full result immediately.
  if (response.status === 200) {
    return response.json();
  }

  // Job queued — start polling.
  if (response.status === 202) {
    const { jobId } = (await response.json()) as { jobId: string; status: string };
    return pollJob(jobId, onStatus);
  }

  // Any other status is an error.
  let errorMessage = 'An error occurred while fetching the analysis.';
  try {
    const errorData = await response.json();
    errorMessage = errorData.message || errorMessage;
  } catch {
    // Ignore JSON parse error, use fallback message.
  }
  throw new Error(errorMessage);
}

async function pollJob(jobId: string, onStatus?: StatusCallback): Promise<SearchResponse> {
  for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
    await sleep(POLL_INTERVAL_MS);

    const res = await fetch(`${API_BASE_URL}/search/${jobId}`);
    if (!res.ok) {
      throw new Error(`Failed to poll job status (HTTP ${res.status}).`);
    }

    const job: JobResponse = await res.json();
    onStatus?.(job.status);

    if (job.status === 'COMPLETED') {
      if (!job.result) {
        throw new Error('Job completed but no result was returned.');
      }
      return job.result;
    }

    if (job.status === 'FAILED') {
      throw new Error(job.error || 'The analysis job failed on the server. Please try again.');
    }

    // PENDING or IN_PROGRESS — keep waiting.
  }

  throw new Error('Analysis timed out after 2 minutes. The server may be busy — please try again.');
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

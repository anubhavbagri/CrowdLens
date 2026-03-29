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
export type StatusCallback = (status: JobStatus) => void;

interface JobStatusResponse {
  jobId: string;
  status: JobStatus;
  result?: SearchResponse;
  error?: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const POLL_INTERVAL_MS = 2000;
const MAX_POLLS = 90; // 3 minutes

export async function searchCrowdLens(
  request: SearchRequest,
  onStatus?: StatusCallback
): Promise<SearchResponse> {
  const res = await fetch(`${API_BASE_URL}/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  // Cache hit — immediate result, no polling needed
  if (res.status === 200) {
    return res.json();
  }

  // Cache miss — job queued, poll for completion
  if (res.status === 202) {
    const { jobId } = await res.json();
    onStatus?.('PENDING');
    return pollJob(jobId, onStatus);
  }

  // Error response
  const err = await res.json().catch(() => ({}));
  throw new Error((err as any).message || `Request failed: ${res.status}`);
}

async function pollJob(jobId: string, onStatus?: StatusCallback): Promise<SearchResponse> {
  for (let i = 0; i < MAX_POLLS; i++) {
    await sleep(POLL_INTERVAL_MS);

    const res = await fetch(`${API_BASE_URL}/search/${jobId}`);
    if (!res.ok) throw new Error(`Poll failed: ${res.status}`);

    const job: JobStatusResponse = await res.json();
    onStatus?.(job.status);

    if (job.status === 'COMPLETED' && job.result) return job.result;
    if (job.status === 'FAILED') throw new Error(job.error || 'Analysis failed. Please try again.');
  }
  throw new Error('Analysis timed out after 3 minutes. Please try again.');
}

function sleep(ms: number) {
  return new Promise<void>((r) => setTimeout(r, ms));
}

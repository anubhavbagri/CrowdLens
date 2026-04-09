export interface Metric {
  label: string;
  score: number;        // 0.0 – 10.0
  explanation: string;
}

export interface EvidenceSnippet {
  text: string;
  source: string;       // e.g. "r/malegrooming"
  permalink: string;
}

export interface SearchResponse {
  id: string;
  query: string;
  productCategory?: string;       // e.g. "Grooming"
  productSubCategory?: string;    // e.g. "Electric Trimmer"
  overallScore: number;           // 0–100
  verdictSentence: string;        // Single crafted sentence
  metrics: Metric[];              // Exactly 4 dynamic metrics
  positives: string[];            // Most praised themes
  complaints: string[];           // Most complained themes
  bestFor: string[];              // Persona descriptors
  avoid: string[];                // Persona descriptors
  evidenceSnippets: EvidenceSnippet[];
  postCount: number;
  sourcePlatforms: string[];
  analyzedAt: string;
  cached: boolean;
}

export interface SearchRequest {
  query: string;
  limit?: number;        // default: 10, max: 50
  maxComments?: number;  // default: 50, max: 100
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export async function searchCrowdLens(request: SearchRequest): Promise<SearchResponse> {
  const response = await fetch(`${API_BASE_URL}/search`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorMessage = 'An error occurred while fetching the analysis.';
    try {
      const errorData = await response.json();
      errorMessage = errorData.message || errorMessage;
    } catch {
      // Ignore JSON parse error for fallback message
    }
    throw new Error(errorMessage);
  }

  return response.json();
}

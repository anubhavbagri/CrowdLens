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
    } catch (e) {
      // Ignore JSON parse error for fallback message
    }
    throw new Error(errorMessage);
  }

  return response.json();
}

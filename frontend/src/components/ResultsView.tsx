'use client';

import { SearchResponse } from '@/lib/api';
import VerdictCard from './VerdictCard';
import MetricsGrid from './MetricsGrid';
import OpinionBlocks from './OpinionBlocks';
import CompetitorCard from './CompetitorCard';
import SharePrompt from './SharePrompt';
import { useShareCard } from '@/lib/useShareCard';

interface ResultsViewProps {
  results: SearchResponse;
  onSearch: (query: string) => void;
}

export default function ResultsView({ results, onSearch }: ResultsViewProps) {
  const { handleShare, handleDownload } = useShareCard(results.query);

  return (
    <div className="w-full max-w-4xl mx-auto space-y-6 pb-20 animate-fade-in">

      {/* Hero verdict card: score + verdict sentence + 4 metric bars + share/download icons */}
      <VerdictCard response={results} />

      {/* Detailed metric breakdown */}
      {results.metrics && results.metrics.length > 0 && (
        <MetricsGrid metrics={results.metrics} />
      )}

      {/* Competitor comparison — after performance breakdown */}
      <CompetitorCard results={results} onSearch={onSearch} />

      {/* Positives / complaints / bestFor / avoid / evidence snippets */}
      <OpinionBlocks results={results} />

      {/* Share prompt toast — appears 2s after results load */}
      <SharePrompt onShare={handleShare} onDownload={handleDownload} delayMs={2000} />

    </div>
  );
}

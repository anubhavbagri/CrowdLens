import { SearchResponse } from '@/lib/api';
import VerdictCard from './VerdictCard';
import MetricsGrid from './MetricsGrid';
import OpinionBlocks from './OpinionBlocks';
import CompetitorCard from './CompetitorCard';

interface ResultsViewProps {
  results: SearchResponse;
  onSearch: (query: string) => void;
}

export default function ResultsView({ results, onSearch }: ResultsViewProps) {
  return (
    <div className="w-full max-w-4xl mx-auto space-y-6 pb-20 animate-fade-in">

      {/* Hero verdict card: score + verdict sentence + 4 metric bars + share/download icons */}
      <VerdictCard response={results} />

      {/* Detailed metric breakdown */}
      {results.metrics && results.metrics.length > 0 && (
        <MetricsGrid metrics={results.metrics} />
      )}

      {/* Positives / complaints / bestFor / avoid / evidence snippets */}
      <OpinionBlocks results={results} />

      {/* Competitor comparison — only shown when productCategory is resolved */}
      <CompetitorCard results={results} onSearch={onSearch} />

    </div>
  );
}

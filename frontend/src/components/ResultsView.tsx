import { SearchResponse } from '@/lib/api';
import VerdictCard from './VerdictCard';
import MetricsGrid from './MetricsGrid';
import OpinionBlocks from './OpinionBlocks';
import ShareCard from './ShareCard';

interface ResultsViewProps {
  results: SearchResponse;
}

export default function ResultsView({ results }: ResultsViewProps) {
  return (
    <div className="w-full max-w-4xl mx-auto space-y-6 pb-20 animate-fade-in">

      {/* Hero verdict card: score + verdict sentence + 4 metric bars */}
      <VerdictCard response={results} />

      {/* Detailed metric breakdown */}
      {results.metrics && results.metrics.length > 0 && (
        <MetricsGrid metrics={results.metrics} />
      )}

      {/* Positives / complaints / bestFor / avoid / evidence snippets */}
      <OpinionBlocks results={results} />

      {/* Shareable card */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
        <ShareCard results={results} />
      </div>

    </div>
  );
}

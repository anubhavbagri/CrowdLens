'use client';

import { CompetitorDto, SearchResponse } from '@/lib/api';
import { ArrowUpRight } from 'lucide-react';

interface CompetitorCardProps {
  results: SearchResponse;
  onSearch: (query: string) => void;
}

function scoreColor(score: number): string {
  if (score >= 75) return 'bg-teal-500';
  if (score >= 55) return 'bg-amber-400';
  return 'bg-red-400';
}

function scoreDelta(competitor: number | null, current: number): string | null {
  if (competitor === null) return null;
  const d = competitor - current;
  if (d > 0) return `+${d}`;
  if (d < 0) return `${d}`;
  return '=';
}

function deltaColor(competitor: number | null, current: number): string {
  if (competitor === null) return '';
  const d = competitor - current;
  if (d > 2) return 'text-teal-600';
  if (d < -2) return 'text-red-500';
  return 'text-gray-400';
}

export default function CompetitorCard({ results, onSearch }: CompetitorCardProps) {
  const { productCategory, productSubCategory, query, overallScore, competitors } = results;

  // Don't render if no category resolved or backend returned no competitors
  if (!productCategory || !competitors || competitors.length === 0) return null;

  // Merge current product into the list for a unified comparison view
  const currentEntry: CompetitorDto = { name: query, score: overallScore, real: true };
  const combinedEntries = [...competitors, currentEntry];

  // Deduplicate by normalized name to safely ignore transient overlap
  const uniqueEntriesMap = new Map<string, CompetitorDto>();
  combinedEntries.forEach(item => {
    uniqueEntriesMap.set(item.name.toLowerCase().trim(), item);
  });

  const allEntries: CompetitorDto[] = Array.from(uniqueEntriesMap.values())
    .sort((a, b) => (b.score ?? -1) - (a.score ?? -1));

  return (
    <div className="bg-white rounded-[2rem] p-8 shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-gray-100">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-bold text-gray-900">Compare with similar products</h3>
          {productCategory && (
            <span className="text-xs text-gray-400 font-medium">
              {productSubCategory ?? productCategory}
            </span>
          )}
        </div>
      </div>

      {/* Competitor rows */}
      <div className="space-y-3">
        {allEntries.map((item) => {
          const isCurrent = item.name === query || item.name?.toLowerCase() === query.toLowerCase();
          const delta = isCurrent ? null : scoreDelta(item.score, overallScore);
          const isEstimate = !isCurrent && !item.real;

          return (
            <div
              key={item.name}
              className={`flex items-center gap-4 p-3 rounded-2xl transition-colors ${
                isCurrent
                  ? 'bg-gray-900 text-white'
                  : 'hover:bg-gray-50 cursor-pointer group'
              }`}
              onClick={() => !isCurrent && onSearch(item.name)}
              title={isCurrent ? 'Current product' : `Search for "${item.name}"`}
            >
              {/* Score badge */}
              <div className="flex-shrink-0 w-10 h-10 flex items-center justify-center">
                {item.score !== null ? (
                  <div
                    className={`w-10 h-10 rounded-2xl flex items-center justify-center text-white text-sm font-bold ${
                      isCurrent ? 'bg-white/20' : scoreColor(item.score)
                    }`}
                  >
                    {item.score}
                  </div>
                ) : (
                  <div className="w-10 h-10 rounded-2xl border-2 border-dashed border-gray-200 flex items-center justify-center text-gray-300 text-sm font-bold">
                    —
                  </div>
                )}
              </div>

              {/* Product name + metadata */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <p className={`text-sm font-semibold truncate capitalize ${
                    isCurrent ? 'text-white' : 'text-gray-800 group-hover:text-gray-900'
                  }`}>
                    {item.name}
                  </p>
                  {isEstimate && (
                    <span className="flex-shrink-0 text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-amber-50 text-amber-600 border border-amber-200">
                      Est.
                    </span>
                  )}
                </div>
                {isCurrent && (
                  <p className="text-xs text-white/60 mt-0.5">Current product</p>
                )}
                {!isCurrent && item.real && item.postCount && (
                  <p className="text-xs text-gray-400 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    {item.postCount} discussions
                  </p>
                )}
                {isEstimate && (
                  <p className="text-xs text-amber-500/80 mt-0.5">AI estimate · Search to analyse</p>
                )}
              </div>

              {/* Delta or action */}
              {!isCurrent && (
                <div className="flex-shrink-0 flex items-center gap-2">
                  {delta && (
                    <span className={`text-xs font-bold ${deltaColor(item.score, overallScore)}`}>
                      {delta}
                    </span>
                  )}
                  {item.score === null ? (
                    <span className="text-xs text-brand-600 font-semibold opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-0.5">
                      Check <ArrowUpRight className="w-3 h-3" />
                    </span>
                  ) : (
                    <ArrowUpRight className="w-3.5 h-3.5 text-gray-300 opacity-0 group-hover:opacity-100 transition-opacity" />
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <p className="mt-4 text-xs text-gray-400">
        Click any product to run a fresh analysis.
        Scores shown only for previously analysed products.
      </p>
    </div>
  );
}

'use client';

import { SearchResponse } from '@/lib/api';
import ScoreCircle from './ScoreCircle';
import { Zap } from 'lucide-react';

interface VerdictCardProps {
  response: SearchResponse;
}

// Compact inline metric bar used inside the verdict card
function MetricPill({ label, score }: { label: string; score: number }) {
  const pct = Math.round((score / 10) * 100);
  const color =
    score >= 8 ? '#14b8a6' : score >= 6 ? '#f59e0b' : '#ef4444';

  return (
    <div className="flex-1 min-w-[120px]">
      <div className="flex justify-between items-center mb-1">
        <span className="text-xs font-semibold text-gray-500 truncate">{label}</span>
        <span className="text-xs font-bold text-gray-800 ml-2">{score.toFixed(1)}</span>
      </div>
      <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-700"
          style={{ width: `${pct}%`, backgroundColor: color }}
        />
      </div>
    </div>
  );
}

export default function VerdictCard({ response }: VerdictCardProps) {
  return (
    <div className="bg-white rounded-[2rem] p-8 md:p-10 shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-gray-100 relative overflow-hidden">
      {/* Decorative dot grid */}
      <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGNpcmNsZSBjeD0iMSIgY3k9IjEiIHI9IjEiIGZpbGw9InJnYmEoMCwwLDAsMC4wNSkiLz48L3N2Zz4=')] [mask-image:linear-gradient(to_bottom,white,transparent)] opacity-50 pointer-events-none" />

      <div className="relative z-10 flex flex-col md:flex-row items-center md:items-start gap-8 md:gap-12">

        {/* Score */}
        <div className="flex flex-col items-center flex-shrink-0">
          <ScoreCircle score={response.overallScore} />
          <span className="mt-2 text-xs font-bold uppercase tracking-widest text-gray-400">
            Community Score
          </span>
        </div>

        {/* Right side */}
        <div className="flex-1 flex flex-col gap-4 pt-1">

          {/* Badges */}
          <div className="flex flex-wrap items-center gap-2">
            {response.productCategory && (
              <span className="px-3 py-1 bg-brand-50 text-brand-700 rounded-full text-xs font-semibold tracking-wide">
                {response.productSubCategory ?? response.productCategory}
              </span>
            )}
            {response.sourcePlatforms?.map(p => (
              <span key={p} className="px-3 py-1 bg-gray-100 text-gray-600 rounded-full text-xs font-semibold capitalize flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-orange-500" />
                {p}
              </span>
            ))}
            <span className="px-3 py-1 bg-gray-100 text-gray-500 rounded-full text-xs font-medium">
              {response.postCount} discussions
            </span>
            {response.cached && (
              <span className="px-3 py-1 bg-yellow-50 text-yellow-700 rounded-full text-xs font-semibold flex items-center gap-1" title="Cached result">
                <Zap className="w-3 h-3 fill-current" /> Cached
              </span>
            )}
          </div>

          {/* Query heading */}
          <h2 className="text-3xl md:text-4xl font-extrabold text-gray-900 leading-tight">
            {response.query}
          </h2>

          {/* Verdict sentence */}
          <p className="text-gray-700 text-base md:text-lg leading-relaxed font-medium bg-gray-50 px-5 py-4 rounded-2xl border border-gray-100 border-l-4 border-l-brand-500">
            {response.verdictSentence}
          </p>

          {/* 4 metrics inline */}
          {response.metrics && response.metrics.length > 0 && (
            <div className="flex flex-wrap gap-x-6 gap-y-3 pt-1">
              {response.metrics.map((m, i) => (
                <MetricPill key={i} label={m.label} score={m.score} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

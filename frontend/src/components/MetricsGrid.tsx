'use client';

import { Metric } from '@/lib/api';

interface MetricsGridProps {
  metrics: Metric[];
}

function scoreLabel(score: number): string {
  if (score >= 9) return 'Excellent';
  if (score >= 7.5) return 'Good';
  if (score >= 6) return 'Fair';
  return 'Weak';
}

function scoreColor(score: number) {
  if (score >= 8) return { bar: '#14b8a6', text: 'text-teal-600', bg: 'bg-teal-50' };
  if (score >= 6.5) return { bar: '#f59e0b', text: 'text-amber-600', bg: 'bg-amber-50' };
  return { bar: '#ef4444', text: 'text-red-500', bg: 'bg-red-50' };
}

export default function MetricsGrid({ metrics }: MetricsGridProps) {
  if (!metrics || metrics.length === 0) return null;

  return (
    <div>
      <h3 className="text-lg font-bold text-gray-900 mb-3">Performance Breakdown</h3>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {metrics.map((m, i) => {
          const { bar, text, bg } = scoreColor(m.score);
          const pct = Math.round((m.score / 10) * 100);
          return (
            <div key={i} className="bg-white rounded-2xl border border-gray-100 p-5 shadow-sm">
              <div className="flex items-start justify-between gap-2 mb-3">
                <span className="font-bold text-gray-900 text-sm">{m.label}</span>
                <div className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold ${bg} ${text}`}>
                  {m.score.toFixed(1)}
                  <span className="font-normal opacity-60">/10</span>
                </div>
              </div>

              {/* Progress bar */}
              <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden mb-3">
                <div
                  className="h-full rounded-full transition-all duration-700"
                  style={{ width: `${pct}%`, backgroundColor: bar }}
                />
              </div>

              <div className="flex items-center justify-between">
                <p className="text-gray-500 text-xs leading-snug flex-1">{m.explanation}</p>
                <span className={`text-xs font-semibold ml-3 flex-shrink-0 ${text}`}>
                  {scoreLabel(m.score)}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

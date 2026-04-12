'use client';

import { useEffect, useState } from 'react';
import { Loader2, MessageSquare } from 'lucide-react';

interface ShimmerResultsProps {
  hints: string[];
}

const GENERIC_HINTS = [
  'Scanning community discussions…',
  'Analyzing user sentiment patterns…',
  'Collecting expert opinions…',
  'Evaluating real-world usage feedback…',
  'Identifying key buying decision factors…',
];

export function ShimmerResults({ hints }: ShimmerResultsProps) {
  const active = hints.length > 0 ? hints : GENERIC_HINTS;
  const isGemini = hints.length > 0;

  const [current, setCurrent] = useState(0);
  const [visible, setVisible] = useState(true);

  // Reset to first hint whenever the hint set changes (generic → gemini)
  useEffect(() => {
    setCurrent(0);
    setVisible(true);
  }, [hints]);

  // Rotate every 2s with a smooth cross-fade
  useEffect(() => {
    if (active.length === 0) return;
    const interval = setInterval(() => {
      setVisible(false);
      setTimeout(() => {
        setCurrent(prev => (prev + 1) % active.length);
        setVisible(true);
      }, 300);
    }, 2300);
    return () => clearInterval(interval);
  }, [active]);

  return (
    <div className="w-full max-w-5xl mx-auto animate-fade-in">

      {/* ── Hint feed ─────────────────────────────────────────────── */}
      <div className="w-full mb-6">
        {/* Label row */}
        <div className="flex items-center gap-2 mb-3 px-1">
          <MessageSquare className="w-4 h-4 text-brand-500" />
          <span className="text-xs font-bold uppercase tracking-widest text-gray-400">
            What we&apos;re analyzing
          </span>
          {/* Dot indicators */}
          <div className="flex gap-1 ml-auto">
            {active.slice(0, 5).map((_, i) => (
              <span
                key={i}
                className={`w-1.5 h-1.5 rounded-full transition-all duration-300 ${
                  i === current % 5 ? 'bg-brand-500 scale-125' : 'bg-gray-200'
                }`}
              />
            ))}
          </div>
        </div>

        {/* Message card */}
        <div
          className="bg-white rounded-2xl border border-gray-100 shadow-sm px-6 py-5 transition-all duration-300"
          style={{ opacity: visible ? 1 : 0, transform: visible ? 'translateY(0)' : 'translateY(6px)' }}
        >
          <div className="flex items-center gap-4">
            <div className="flex-shrink-0">
              <Loader2 className="w-6 h-6 text-brand-500 animate-spin" />
            </div>
            <div className="flex-1">
              <p className="text-gray-800 text-base font-medium leading-relaxed">
                {active[current]}
              </p>
              {isGemini && (
                <span className="mt-1.5 inline-block text-xs text-brand-400 font-medium">
                  AI-tailored insight
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Counter */}
        <p className="text-center text-xs text-gray-300 mt-2">
          {current + 1}&nbsp;/&nbsp;{active.length}
        </p>
      </div>

      {/* ── Ghost skeleton ────────────────────────────────────────── */}
      <div className="space-y-8">
        <div className="flex flex-col md:flex-row gap-8 bg-white p-8 rounded-3xl shadow-sm border border-gray-100">
          <div className="flex-shrink-0 flex items-center justify-center">
            <div className="w-32 h-32 rounded-full skeleton-shimmer" />
          </div>
          <div className="flex-1 space-y-4 py-2">
            <div className="h-4 w-24 rounded-full skeleton-shimmer mb-2" />
            <div className="h-8 w-3/4 rounded-lg skeleton-shimmer" />
            <div className="space-y-2 pt-4">
              <div className="h-4 w-full rounded-md skeleton-shimmer" />
              <div className="h-4 w-5/6 rounded-md skeleton-shimmer" />
              <div className="h-4 w-4/6 rounded-md skeleton-shimmer" />
            </div>
          </div>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 space-y-6">
            <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 h-64 skeleton-shimmer opacity-80" />
            <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 h-80 skeleton-shimmer opacity-80" />
          </div>
          <div className="space-y-4">
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 h-32 skeleton-shimmer opacity-80" />
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 h-40 skeleton-shimmer opacity-80" />
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 h-48 skeleton-shimmer opacity-80" />
          </div>
        </div>
      </div>
    </div>
  );
}

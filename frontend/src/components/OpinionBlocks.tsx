'use client';

import { SearchResponse } from '@/lib/api';
import { ThumbsUp, ThumbsDown, Users, UserX, ExternalLink } from 'lucide-react';

interface OpinionBlocksProps {
  results: SearchResponse;
}

function ListBlock({
  icon,
  title,
  items,
  accent,
}: {
  icon: React.ReactNode;
  title: string;
  items: string[];
  accent: string;
}) {
  if (!items || items.length === 0) return null;
  return (
    <div className={`bg-white rounded-2xl p-6 border border-gray-100 shadow-sm`}>
      <div className={`flex items-center gap-2 mb-4`}>
        <span className={`p-1.5 rounded-lg ${accent}`}>{icon}</span>
        <h3 className="font-bold text-gray-900 text-sm uppercase tracking-widest">{title}</h3>
      </div>
      <ul className="space-y-2">
        {items.map((item, i) => (
          <li key={i} className="flex items-start gap-2 text-gray-700 text-sm leading-snug">
            <span className="mt-1 w-1.5 h-1.5 rounded-full bg-gray-300 flex-shrink-0" />
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function OpinionBlocks({ results }: OpinionBlocksProps) {
  return (
    <div className="space-y-6">

      {/* What people say — positives + complaints side by side */}
      <div>
        <h3 className="text-lg font-bold text-gray-900 mb-3">What People Are Saying</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ListBlock
            icon={<ThumbsUp className="w-4 h-4 text-green-600" />}
            title="What they love"
            items={results.positives ?? []}
            accent="bg-green-50"
          />
          <ListBlock
            icon={<ThumbsDown className="w-4 h-4 text-red-500" />}
            title="What they complain about"
            items={results.complaints ?? []}
            accent="bg-red-50"
          />
        </div>
      </div>

      {/* Best for / Avoid side by side */}
      <div>
        <h3 className="text-lg font-bold text-gray-900 mb-3">Who Is This For?</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ListBlock
            icon={<Users className="w-4 h-4 text-brand-600" />}
            title="Best for"
            items={results.bestFor ?? []}
            accent="bg-brand-50"
          />
          <ListBlock
            icon={<UserX className="w-4 h-4 text-gray-500" />}
            title="Not for"
            items={results.avoid ?? []}
            accent="bg-gray-100"
          />
        </div>
      </div>

      {/* Evidence snippets */}
      {results.evidenceSnippets && results.evidenceSnippets.length > 0 && (
        <div>
          <h3 className="text-lg font-bold text-gray-900 mb-3">From the Community</h3>
          <div className="space-y-3">
            {results.evidenceSnippets.map((s, i) => (
              <div key={i} className="bg-white rounded-2xl border border-gray-100 px-5 py-4 shadow-sm flex gap-4 items-start">
                <span className="text-brand-500 font-black text-lg leading-none mt-0.5">"</span>
                <div className="flex-1">
                  <p className="text-gray-700 text-sm leading-relaxed">{s.text}</p>
                  <div className="mt-2 flex items-center gap-3 text-xs text-gray-400">
                    <span className="font-semibold text-gray-500">{s.source}</span>
                    {s.permalink && (
                      <a
                        href={s.permalink}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-1 hover:text-brand-600 transition-colors"
                      >
                        <ExternalLink className="w-3 h-3" /> view
                      </a>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

    </div>
  );
}

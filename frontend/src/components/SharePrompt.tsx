'use client';

import { useEffect, useState } from 'react';
import { Share2, X, Download } from 'lucide-react';

interface SharePromptProps {
  onShare: () => void;
  onDownload: () => void;
  delayMs?: number;
}

/**
 * Animated toast that slides in from the bottom after a delay,
 * prompting the user to share or download the verdict card.
 */
export default function SharePrompt({ onShare, onDownload, delayMs = 2000 }: SharePromptProps) {
  const [visible, setVisible] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), delayMs);
    return () => clearTimeout(timer);
  }, [delayMs]);

  if (dismissed || !visible) return null;

  return (
    <div className="fixed top-6 left-1/2 -translate-x-1/2 z-50 animate-slide-down">
      <div className="flex items-center gap-4 bg-white/95 backdrop-blur-lg rounded-2xl shadow-2xl border border-gray-200/60 px-5 py-4 max-w-md">

        {/* Icon */}
        <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center shrink-0">
          <Share2 className="w-5 h-5 text-brand-600" />
        </div>

        {/* Text */}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-gray-900">Share your insights!</p>
          <p className="text-xs text-gray-500">Download or share the verdict card to socials</p>
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => { onShare(); setDismissed(true); }}
            className="px-3.5 py-2 rounded-xl bg-brand-600 text-white text-xs font-semibold hover:bg-brand-700 transition-colors shadow-sm"
          >
            Share
          </button>
          <button
            onClick={() => { onDownload(); setDismissed(true); }}
            className="w-8 h-8 flex items-center justify-center rounded-xl bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors"
            title="Download"
          >
            <Download className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Dismiss */}
        <button
          onClick={() => setDismissed(true)}
          className="w-6 h-6 flex items-center justify-center rounded-full text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
}

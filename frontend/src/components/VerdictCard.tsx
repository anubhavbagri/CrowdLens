'use client';

import { useState } from 'react';
import { SearchResponse } from '@/lib/api';
import ScoreCircle from './ScoreCircle';
import { Zap, Download, Share2, Check } from 'lucide-react';
import { toPng, toBlob as ht2iBlob } from 'html-to-image';
import { ShareCardVisual } from './ShareCard';

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
  const [sharing, setSharing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [copied, setCopied] = useState(false);

  const slug = response.query.replace(/\s+/g, '-').toLowerCase();

  const getCardEl = () => {
    const el = document.getElementById('share-card-visual');
    if (!el) throw new Error('Card element not found');
    return el;
  };

  const handleShare = async () => {
    setSharing(true);
    try {
      const blob = await ht2iBlob(getCardEl(), { pixelRatio: 2 });
      if (!blob) return;
      const file = new File([blob], `crowdlens-${slug}.png`, { type: 'image/png' });
      if (navigator.canShare?.({ files: [file] })) {
        await navigator.share({ files: [file], title: `CrowdLens: ${response.query}` });
      } else {
        // Desktop fallback: copy to clipboard
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch (e) {
      console.error('Share failed', e);
    } finally {
      setSharing(false);
    }
  };

  const handleDownload = async () => {
    setDownloading(true);
    try {
      const dataUrl = await toPng(getCardEl(), { pixelRatio: 2 });
      const link = document.createElement('a');
      link.download = `crowdlens-${slug}.png`;
      link.href = dataUrl;
      link.click();
    } catch (e) {
      console.error('Download failed', e);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <>
      {/* Off-screen capture target — rendered for html-to-image, invisible to users */}
      <div style={{ position: 'fixed', left: '-9999px', top: 0, pointerEvents: 'none' }} aria-hidden>
        <ShareCardVisual results={response} />
      </div>

      <div className="bg-white rounded-[2rem] p-8 md:p-10 shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-gray-100 relative overflow-hidden">
        {/* Decorative dot grid */}
        <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGNpcmNsZSBjeD0iMSIgY3k9IjEiIHI9IjEiIGZpbGw9InJnYmEoMCwwLDAsMC4wNSkiLz48L3N2Zz4=')] [mask-image:linear-gradient(to_bottom,white,transparent)] opacity-50 pointer-events-none" />

        {/* Share / Download icon buttons */}
        <div className="absolute top-4 right-4 md:top-6 md:right-6 z-20 flex items-center gap-1.5">
          <button
            onClick={handleShare}
            disabled={sharing}
            title={copied ? 'Copied to clipboard!' : 'Share'}
            className="w-8 h-8 flex items-center justify-center rounded-full bg-white border border-gray-100 shadow-sm hover:bg-gray-50 text-gray-400 hover:text-brand-600 transition-colors disabled:opacity-50"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-green-500" /> : <Share2 className="w-3.5 h-3.5" />}
          </button>
          <button
            onClick={handleDownload}
            disabled={downloading}
            title="Download as image"
            className="w-8 h-8 flex items-center justify-center rounded-full bg-white border border-gray-100 shadow-sm hover:bg-gray-50 text-gray-400 hover:text-brand-600 transition-colors disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5" />
          </button>
        </div>

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
    </>
  );
}

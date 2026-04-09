'use client';

import { useRef, useState } from 'react';
import { SearchResponse } from '@/lib/api';
import { Download, Share2, Check } from 'lucide-react';
import html2canvas from 'html2canvas';

interface ShareCardProps {
  results: SearchResponse;
}

// The visual card — this exact div is what gets screenshot'd
export function ShareCardVisual({ results }: { results: SearchResponse }) {
  const scoreColor =
    results.overallScore >= 75
      ? '#14b8a6'
      : results.overallScore >= 50
      ? '#f59e0b'
      : '#ef4444';

  return (
    <div
      id="share-card-visual"
      style={{
        width: 480,
        background: 'white',
        borderRadius: 24,
        padding: '32px 32px 24px',
        fontFamily: "'Inter', sans-serif",
        border: '1px solid #f3f4f6',
        boxShadow: '0 8px 30px rgba(0,0,0,0.06)',
      }}
    >
      {/* Header: Score + product name */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 20, marginBottom: 20 }}>
        {/* Score bubble */}
        <div
          style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            background: `${scoreColor}15`,
            border: `3px solid ${scoreColor}`,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <span style={{ fontSize: 22, fontWeight: 900, color: scoreColor, lineHeight: 1 }}>
            {results.overallScore}
          </span>
          <span style={{ fontSize: 9, color: '#9ca3af', fontWeight: 600, letterSpacing: 0.5 }}>
            /100
          </span>
        </div>

        <div style={{ flex: 1, paddingTop: 4 }}>
          {results.productSubCategory && (
            <div
              style={{
                display: 'inline-block',
                fontSize: 10,
                fontWeight: 700,
                color: '#0d9488',
                background: '#f0fdfa',
                padding: '2px 10px',
                borderRadius: 20,
                letterSpacing: 0.5,
                marginBottom: 6,
                textTransform: 'uppercase',
              }}
            >
              {results.productSubCategory}
            </div>
          )}
          <div style={{ fontSize: 20, fontWeight: 800, color: '#111827', lineHeight: 1.25 }}>
            {results.query}
          </div>
        </div>
      </div>

      {/* Verdict sentence */}
      <div
        style={{
          fontSize: 13,
          color: '#374151',
          lineHeight: 1.6,
          fontStyle: 'italic',
          borderLeft: `3px solid ${scoreColor}`,
          paddingLeft: 12,
          marginBottom: 20,
        }}
      >
        "{results.verdictSentence}"
      </div>

      {/* 4 metrics */}
      {results.metrics && results.metrics.length > 0 && (
        <div style={{ marginBottom: 20 }}>
          {results.metrics.map((m, i) => {
            const pct = Math.round((m.score / 10) * 100);
            const barColor =
              m.score >= 8 ? '#14b8a6' : m.score >= 6 ? '#f59e0b' : '#ef4444';
            return (
              <div key={i} style={{ marginBottom: i < results.metrics.length - 1 ? 10 : 0 }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: 12,
                    marginBottom: 4,
                  }}
                >
                  <span style={{ fontWeight: 600, color: '#374151' }}>{m.label}</span>
                  <span style={{ fontWeight: 800, color: '#111827' }}>{m.score.toFixed(1)}</span>
                </div>
                <div
                  style={{
                    height: 5,
                    background: '#f3f4f6',
                    borderRadius: 9999,
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      height: '100%',
                      width: `${pct}%`,
                      background: barColor,
                      borderRadius: 9999,
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Footer */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          paddingTop: 14,
          borderTop: '1px solid #f3f4f6',
        }}
      >
        <span style={{ fontSize: 10, color: '#9ca3af', fontWeight: 500 }}>
          Based on {results.postCount} community discussions
        </span>
        <span
          style={{
            fontSize: 11,
            fontWeight: 800,
            color: '#0d9488',
            letterSpacing: 0.5,
            textTransform: 'uppercase',
          }}
        >
          CrowdLens
        </span>
      </div>
    </div>
  );
}

export default function ShareCard({ results }: ShareCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const [copied, setCopied] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const captureCard = async (): Promise<HTMLCanvasElement> => {
    const element = document.getElementById('share-card-visual');
    if (!element) throw new Error('Card element not found');
    return html2canvas(element, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
    });
  };

  const handleDownload = async () => {
    setDownloading(true);
    try {
      const canvas = await captureCard();
      const link = document.createElement('a');
      link.download = `crowdlens-${results.query.replace(/\s+/g, '-').toLowerCase()}.png`;
      link.href = canvas.toDataURL('image/png');
      link.click();
    } catch (e) {
      console.error('Download failed', e);
    } finally {
      setDownloading(false);
    }
  };

  const handleCopy = async () => {
    try {
      const canvas = await captureCard();
      canvas.toBlob(async (blob) => {
        if (!blob) return;
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': blob }),
        ]);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    } catch (e) {
      console.error('Copy failed', e);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-bold text-gray-900">Share Verdict</h3>
        <div className="flex items-center gap-2">
          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 px-4 py-2 text-sm font-semibold text-gray-700 bg-white border border-gray-200 rounded-full hover:bg-gray-50 transition-colors shadow-sm"
          >
            {copied ? (
              <><Check className="w-4 h-4 text-green-500" /> Copied!</>
            ) : (
              <><Share2 className="w-4 h-4" /> Copy Image</>
            )}
          </button>
          <button
            onClick={handleDownload}
            disabled={downloading}
            className="flex items-center gap-1.5 px-4 py-2 text-sm font-semibold text-white bg-brand-600 rounded-full hover:bg-brand-700 transition-colors shadow-sm disabled:opacity-60"
          >
            <Download className="w-4 h-4" />
            {downloading ? 'Saving…' : 'Download'}
          </button>
        </div>
      </div>

      {/* The card preview */}
      <div ref={cardRef} className="overflow-x-auto">
        <div className="inline-block rounded-[1.5rem] shadow-lg ring-1 ring-gray-100">
          <ShareCardVisual results={results} />
        </div>
      </div>

      <p className="text-xs text-gray-400 text-center">
        Download as PNG to share on WhatsApp, Instagram, X, or LinkedIn
      </p>
    </div>
  );
}

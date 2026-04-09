'use client';

import { SearchResponse } from '@/lib/api';

interface ShareCardProps {
  results: SearchResponse;
}

// The visual card — rendered off-screen and captured by html-to-image in VerdictCard
export function ShareCardVisual({ results }: ShareCardProps) {
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
          {/* Badges: subcategory + source platforms + discussions count */}
          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 5, marginBottom: 8 }}>
            {results.productSubCategory && (
              <span
                style={{
                  display: 'inline-block',
                  fontSize: 10,
                  fontWeight: 700,
                  lineHeight: '16px',
                  color: '#0d9488',
                  background: '#f0fdfa',
                  padding: '2px 8px',
                  borderRadius: 20,
                  letterSpacing: 0.5,
                  textTransform: 'uppercase',
                }}
              >
                {results.productSubCategory}
              </span>
            )}
            {results.sourcePlatforms?.map((p) => (
              <span
                key={p}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  fontSize: 9,
                  fontWeight: 600,
                  lineHeight: '16px',
                  color: '#6b7280',
                  background: '#f3f4f6',
                  padding: '2px 8px',
                  borderRadius: 20,
                  textTransform: 'capitalize',
                }}
              >
                <span style={{ width: 5, height: 5, borderRadius: '50%', background: '#f97316', display: 'inline-block', flexShrink: 0 }} />
                {p}
              </span>
            ))}
            <span
              style={{
                display: 'inline-block',
                fontSize: 9,
                fontWeight: 600,
                lineHeight: '16px',
                color: '#6b7280',
                background: '#f3f4f6',
                padding: '2px 8px',
                borderRadius: 20,
              }}
            >
              {results.postCount} discussions
            </span>
          </div>
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
        &ldquo;{results.verdictSentence}&rdquo;
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

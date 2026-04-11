import { useEffect, useMemo, useRef, useState } from 'react';
import Fuse from 'fuse.js';
import { SEARCH_SUGGESTIONS } from './searchSuggestions';

// ── Fuse.js (instant static fallback) ────────────────────────────────────────
const fuse = new Fuse(SEARCH_SUGGESTIONS, {
  threshold: 0.4,
  minMatchCharLength: 2,
  includeScore: true,
  ignoreLocation: true,
  distance: 200,
});

function staticSuggestions(query: string, limit: number): string[] {
  const trimmed = query.trim();
  if (trimmed.length < 2) return [];
  return fuse.search(trimmed, { limit }).map((r) => r.item);
}

// ── Live Amazon suggestions via Next.js proxy ────────────────────────────────
const DEBOUNCE_MS = 300;

/**
 * Returns product autocomplete suggestions for the given query.
 *
 * Strategy:
 *  1. Immediately returns Fuse.js static results (zero latency).
 *  2. After 300ms debounce, fetches live results from Amazon India via
 *     the /api/suggest proxy route and replaces the static results.
 *  3. Falls back silently to static results if the API call fails.
 */
export function useSearchSuggestions(query: string, limit = 6): string[] {
  const [liveSuggestions, setLiveSuggestions] = useState<string[] | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const staticResults = useMemo(() => staticSuggestions(query, limit), [query, limit]);

  useEffect(() => {
    const trimmed = query.trim();

    // Reset live results and cancel in-flight requests when query changes
    setLiveSuggestions(null);
    abortRef.current?.abort();
    if (timerRef.current) clearTimeout(timerRef.current);

    if (trimmed.length < 2) return;

    // Debounce the API call so we don't fire on every keystroke
    timerRef.current = setTimeout(async () => {
      const controller = new AbortController();
      abortRef.current = controller;

      try {
        const res = await fetch(
          `/api/suggest?q=${encodeURIComponent(trimmed)}`,
          { signal: controller.signal }
        );
        if (!res.ok) return;

        const data: string[] = await res.json();
        if (data.length > 0) {
          setLiveSuggestions(data.slice(0, limit));
        }
      } catch {
        // AbortError (from query change) or network failure — stay on static
      }
    }, DEBOUNCE_MS);

    return () => {
      abortRef.current?.abort();
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [query, limit]);

  // Live results replace static results once available
  return liveSuggestions ?? staticResults;
}

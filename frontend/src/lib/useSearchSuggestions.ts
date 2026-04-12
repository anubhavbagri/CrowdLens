import { useEffect, useRef, useState } from 'react';

const DEBOUNCE_MS = 300;

/**
 * Returns product autocomplete suggestions for the given query.
 *
 * Strategy:
 *  1. Returns an empty array immediately.
 *  2. After 300ms debounce, fetches live results from Amazon via
 *     the /api/suggest proxy route.
 */
export function useSearchSuggestions(query: string, limit = 6): string[] {
  const [liveSuggestions, setLiveSuggestions] = useState<string[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const trimmed = query.trim();

    // Reset live results and cancel in-flight requests when query changes
    setLiveSuggestions([]);
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
        setLiveSuggestions(data.slice(0, limit));
      } catch {
        // AbortError (from query change) or network failure — fail silently
      }
    }, DEBOUNCE_MS);

    return () => {
      abortRef.current?.abort();
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [query, limit]);

  return liveSuggestions;
}

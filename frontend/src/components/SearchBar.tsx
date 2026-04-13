'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { Search, Loader2 } from 'lucide-react';
import { useSearchSuggestions } from '@/lib/useSearchSuggestions';

interface SearchBarProps {
  onSearch: (query: string) => void;
  isLoading: boolean;
  variant?: 'hero' | 'navbar';
  initialValue?: string;
}

export default function SearchBar({
  onSearch,
  isLoading,
  variant = 'hero',
  initialValue = '',
}: SearchBarProps) {
  const [query, setQuery] = useState(initialValue);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const suggestions = useSearchSuggestions(query);
  const showDropdown = open && suggestions.length > 0 && !isLoading;

  const isHero = variant === 'hero';

  // Close dropdown when clicking outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setActiveIndex(-1);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const submit = useCallback(
    (value: string) => {
      const trimmed = value.trim();
      if (!trimmed || isLoading) return;
      setOpen(false);
      setActiveIndex(-1);
      onSearch(trimmed);
    },
    [isLoading, onSearch]
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    submit(query);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!showDropdown) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, -1));
    } else if (e.key === 'Enter' && activeIndex >= 0) {
      e.preventDefault();
      const selected = suggestions[activeIndex];
      setQuery(selected);
      submit(selected);
    } else if (e.key === 'Escape') {
      setOpen(false);
      setActiveIndex(-1);
    }
  };

  const handleSuggestionClick = (suggestion: string) => {
    setQuery(suggestion);
    submit(suggestion);
    inputRef.current?.focus();
  };

  return (
    <div
      ref={containerRef}
      className={`relative w-full ${isHero ? 'max-w-2xl mx-auto' : 'max-w-xl'} transition-all duration-300 ease-in-out`}
    >
      <form onSubmit={handleSubmit}>
        <div
          className={`relative flex items-center w-full rounded-xl overflow-hidden ${isHero
              ? 'bg-white h-14 shadow-xl border border-gray-200 hover:border-gray-300 focus-within:ring-2 focus-within:ring-gray-300 focus-within:border-gray-400 transition-all duration-300'
              : 'bg-gray-100 h-10 border border-gray-200 focus-within:ring-2 focus-within:ring-gray-300 focus-within:bg-white transition-all duration-300 rounded-lg'
            } ${showDropdown ? 'rounded-b-none border-b-transparent' : ''}`}
        >
          <div className={`flex items-center justify-center text-gray-400 ${isHero ? 'pl-6 pr-4' : 'pl-4 pr-3'}`}>
            <Search className={`w-5 h-5 ${isHero ? 'text-gray-500' : 'text-gray-400'}`} />
          </div>

          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setOpen(true);
              setActiveIndex(-1);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={handleKeyDown}
            placeholder={isHero ? 'Ask about any product...' : 'Search...'}
            className={`flex-1 bg-transparent border-none outline-none text-gray-900 placeholder-gray-500 w-full ${isHero ? 'text-lg' : 'text-sm'
              }`}
            disabled={isLoading}
            autoComplete="off"
            aria-autocomplete="list"
            aria-expanded={showDropdown}
            aria-activedescendant={activeIndex >= 0 ? `suggestion-${activeIndex}` : undefined}
          />

          <button
            type="submit"
            disabled={!query.trim() || isLoading}
            className={`h-full flex items-center justify-center transition-all ${isHero
                ? 'px-6 bg-black text-white hover:bg-gray-800 disabled:bg-gray-200 disabled:text-gray-400'
                : 'px-4 text-gray-900 hover:text-black disabled:text-gray-400'
              }`}
          >
            {isLoading ? (
              <Loader2 className={`animate-spin ${isHero ? 'h-6 w-6' : 'h-5 w-5'}`} />
            ) : (
              <span className={`${isHero ? 'font-medium' : 'sr-only'}`}>Search</span>
            )}
          </button>
        </div>
      </form>

      {/* Suggestions dropdown */}
      {showDropdown && (
        <ul
          role="listbox"
          className={`absolute z-50 w-full bg-white border border-gray-200 border-t-0 rounded-b-xl shadow-xl overflow-hidden`}
        >
          {suggestions.map((s, i) => (
            <li
              key={s}
              id={`suggestion-${i}`}
              role="option"
              aria-selected={i === activeIndex}
              onMouseDown={(e) => {
                // prevent blur before click registers
                e.preventDefault();
                handleSuggestionClick(s);
              }}
              onMouseEnter={() => setActiveIndex(i)}
              className={`flex items-center gap-3 px-5 py-3 cursor-pointer text-sm transition-colors ${i === activeIndex
                  ? 'bg-gray-100 text-gray-900'
                  : 'text-gray-700 hover:bg-gray-50'
                } ${i !== suggestions.length - 1 ? 'border-b border-gray-100' : ''}`}
            >
              <Search className="w-3.5 h-3.5 text-gray-400 shrink-0" />
              <span>{s}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

'use client';

import { useState } from 'react';
import { Search, Loader2 } from 'lucide-react';

interface SearchBarProps {
  onSearch: (query: string) => void;
  isLoading: boolean;
  variant?: 'hero' | 'navbar';
  initialValue?: string;
}

export default function SearchBar({ onSearch, isLoading, variant = 'hero', initialValue = '' }: SearchBarProps) {
  const [query, setQuery] = useState(initialValue);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim() && !isLoading) {
      onSearch(query.trim());
    }
  };

  const isHero = variant === 'hero';

  return (
    <form
      onSubmit={handleSubmit}
      className={`relative w-full ${isHero ? 'max-w-2xl mx-auto' : 'max-w-xl'} transition-all duration-300 ease-in-out`}
    >
      <div className={`relative flex items-center w-full rounded-xl overflow-hidden ${
        isHero 
          ? 'bg-white h-14 shadow-xl border border-gray-200 hover:border-gray-300 focus-within:ring-2 focus-within:ring-gray-300 focus-within:border-gray-400 transition-all duration-300' 
          : 'bg-gray-100 h-10 border border-gray-200 focus-within:ring-2 focus-within:ring-gray-300 focus-within:bg-white transition-all duration-300 rounded-lg'
      }`}>
        <div className={`flex items-center justify-center text-gray-400 ${isHero ? 'pl-6 pr-4' : 'pl-4 pr-3'}`}>
          <Search className={`w-5 h-5 ${isHero ? 'text-gray-500' : 'text-gray-400'}`} />
        </div>
        
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={isHero ? "Ask about any product, service, or experience..." : "Search..."}
          className={`flex-1 bg-transparent border-none outline-none text-gray-900 placeholder-gray-500 w-full ${
            isHero ? 'text-lg' : 'text-sm'
          }`}
          disabled={isLoading}
        />

        <button
          type="submit"
          disabled={!query.trim() || isLoading}
          className={`h-full flex items-center justify-center transition-all ${
            isHero ? 'px-6 bg-black text-white hover:bg-gray-800 disabled:bg-gray-200 disabled:text-gray-400' : 'px-4 text-gray-900 hover:text-black disabled:text-gray-400'
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
  );
}

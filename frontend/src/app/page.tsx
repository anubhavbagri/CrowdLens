'use client';

import { useState } from 'react';
import Image from 'next/image';
import SearchBar from '@/components/SearchBar';
import Navbar from '@/components/Navbar';
import { searchCrowdLens, SearchResponse, JobStatus } from '@/lib/api';
import { ShimmerResults } from '@/components/ShimmerSkeleton';
import ResultsView from '@/components/ResultsView';
import { AlertTriangle } from 'lucide-react';

export default function Home() {
  const [appState, setAppState] = useState<'idle' | 'loading' | 'results' | 'error'>('idle');
  const [query, setQuery] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [results, setResults] = useState<SearchResponse | null>(null);
  const [jobStatus, setJobStatus] = useState<JobStatus | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const handleSearch = async (newQuery: string) => {
    setQuery(newQuery);
    setAppState('loading');
    setErrorMsg('');
    setJobStatus(null);
    setElapsedSeconds(0);
    window.scrollTo({ top: 0, behavior: 'smooth' });

    const startTime = Date.now();
    const timer = setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startTime) / 1000));
    }, 1000);

    try {
      const data = await searchCrowdLens({ query: newQuery }, setJobStatus);
      setResults(data);
      setAppState('results');
    } catch (err: any) {
      setErrorMsg(err.message || 'An unexpected error occurred.');
      setAppState('error');
    } finally {
      clearInterval(timer);
    }
  };

  return (
    <main className="min-h-screen flex flex-col relative w-full overflow-hidden">
      {/* Background Hero Illustration - Only visible when idle */}
      <div
        className={`absolute inset-0 z-0 transition-opacity duration-1000 ease-in-out ${appState === 'idle' ? 'opacity-100' : 'opacity-0 pointer-events-none'
          }`}
      >
        <Image
          src="/pattern-grey-wide.png"
          alt="Hero abstract background"
          fill
          priority
          className="object-cover object-center opacity-30"
          quality={100}
        />
        {/* Soft overlay gradient to ensure text readability */}
        <div className="absolute inset-0 bg-gradient-to-b from-white/10 via-white/50 to-gray-50/90" />
      </div>

      {/* Navbar appearing on search */}
      {appState !== 'idle' && (
        <Navbar onSearch={handleSearch} isLoading={appState === 'loading'} currentQuery={query} />
      )}

      {/* Main Content Area */}
      <div className={`relative z-10 flex-1 flex flex-col items-center w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 transition-all duration-700 ${appState === 'idle' ? 'justify-center min-h-[80vh]' : 'pt-8'
        }`}>

        {/* Hero Search View */}
        {appState === 'idle' && (
          <div className="w-full text-center max-w-4xl mx-auto animate-fade-in space-y-6">
            <div className="space-y-4">
              <h1 className="grid gap-y-2 text-4xl sm:text-5xl md:text-7xl font-extrabold tracking-tight text-gray-900 drop-shadow-sm">
                Real People, 
                <span>
                  Real Opinions.
                </span>
              </h1>
              <p className="text-lg sm:text-xl text-gray-700 max-w-2xl mx-auto font-medium">
                Search millions of authentic Reddit discussions to get unbiased insights on any product, service, or experience.
              </p>
            </div>

            <div className="w-full max-w-2xl mx-auto transform translate-y-4 hover:-translate-y-1 transition-transform duration-300">
              <SearchBar onSearch={handleSearch} isLoading={false} variant="hero" />
            </div>

            <div className="pt-8 flex flex-wrap justify-center gap-3 opacity-90">
              {['Pondicherry Trip', 'Triumph Scrambler 400', 'Sony WH-1000XM5', 'Notion vs Obsidian'].map((suggestion) => (
                <button
                  key={suggestion}
                  onClick={() => handleSearch(suggestion)}
                  className="px-4 py-2 rounded-full bg-white/60 hover:bg-white text-gray-700 text-sm font-medium border border-gray-200/50 shadow-sm transition-all hover:shadow-md hover:text-gray-900 backdrop-blur-sm"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Loading State */}
        {appState === 'loading' && (
          <div className="w-full animate-fade-in py-12">
            <ShimmerResults jobStatus={jobStatus} elapsedSeconds={elapsedSeconds} />
          </div>
        )}

        {/* Error State */}
        {appState === 'error' && (
          <div className="w-full max-w-2xl mx-auto mt-20 text-center animate-fade-in bg-white p-8 rounded-2xl shadow-xl border border-red-100">
            <div className="w-16 h-16 bg-red-100 text-red-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <AlertTriangle className="w-8 h-8" />
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-2">Analysis Failed</h3>
            <p className="text-red-500 mb-8">{errorMsg}</p>
            <button
              onClick={() => handleSearch(query)}
              className="px-6 py-3 bg-brand-600 text-white rounded-full font-medium hover:bg-brand-700 transition-colors shadow-md hover:shadow-lg"
            >
              Try Again
            </button>
          </div>
        )}

        {/* Results State */}
        {appState === 'results' && results && (
          <div className="w-full animate-slide-up mt-8">
            <ResultsView results={results} />
          </div>
        )}

      </div>
    </main>
  );
}

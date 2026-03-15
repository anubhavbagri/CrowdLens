'use client';

import Link from 'next/link';
import SearchBar from './SearchBar';
import { Aperture } from 'lucide-react';

interface NavbarProps {
  onSearch: (query: string) => void;
  isLoading: boolean;
  currentQuery: string;
}

export default function Navbar({ onSearch, isLoading, currentQuery }: NavbarProps) {
  return (
    <header className="sticky top-0 z-50 w-full bg-white/80 backdrop-blur-md border-b border-gray-200 text-gray-900 shadow-sm animate-slide-down transition-colors duration-300">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        <Link href="/" onClick={() => window.location.reload()} className="flex items-center space-x-2 shrink-0 group">
          <div className="w-8 h-8 rounded-lg bg-black flex items-center justify-center group-hover:bg-gray-800 transition-colors">
            <Aperture className="w-5 h-5 text-white" />
          </div>
          <span className="text-xl font-bold tracking-tight">CrowdLens</span>
        </Link>

        <div className="flex-1 max-w-2xl mx-8 hidden sm:block">
          <SearchBar 
            onSearch={onSearch} 
            isLoading={isLoading} 
            variant="navbar" 
            initialValue={currentQuery}
          />
        </div>

        <div className="flex items-center space-x-4">
           {/* Placeholder for future features like 'Login' or 'History' */}
           <a href="https://github.com/AnubhavBagri/CrowdLens" target="_blank" rel="noopener noreferrer" className="text-gray-500 hover:text-gray-900 font-medium transition-colors">
              GitHub
           </a>
        </div>
      </div>
    </header>
  );
}

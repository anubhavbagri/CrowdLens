'use client';

import { useEffect, useState, useRef } from 'react';
import { Clock, Folder, Star, ChevronDown, Search } from 'lucide-react';
import { fetchTrending, TrendingItem, CategoryItem, TrendingResponse } from '@/lib/api';

interface TrendingSectionProps {
  onSearch: (query: string) => void;
}

export default function TrendingSection({ onSearch }: TrendingSectionProps) {
  const [data, setData] = useState<TrendingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [expandedCategory, setExpandedCategory] = useState<string | null>(null);

  useEffect(() => {
    fetchTrending()
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="w-full max-w-3xl mx-auto pt-10 animate-fade-in">
        <div className="flex flex-wrap justify-center gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="h-10 w-36 rounded-full skeleton-shimmer"
            />
          ))}
        </div>
      </div>
    );
  }

  if (!data) return null;

  const { trending, popularCategories } = data;

  if (trending.length === 0 && popularCategories.length === 0) return null;

  return (
    <div className="w-full max-w-3xl mx-auto pt-8 space-y-8 animate-fade-in">

      {/* ── Recent Searches ── */}
      {trending.length > 0 && (
        <Section icon={<Clock className="w-4 h-4" />} label="Recent Searches">
          <div className="flex flex-wrap justify-center gap-2.5">
            {trending.map((item) => (
              <SearchPill key={item.query} item={item} onClick={() => onSearch(item.query)} />
            ))}
          </div>
        </Section>
      )}

      {/* ── Popular Categories ── */}
      {popularCategories.length > 0 && (
        <Section icon={<Folder className="w-4 h-4" />} label="Popular Categories">
          <div className="flex flex-wrap justify-center gap-2.5">
            {popularCategories.map((cat) => (
              <CategoryPill
                key={cat.name}
                category={cat}
                isExpanded={expandedCategory === cat.name}
                onToggle={() =>
                  setExpandedCategory(expandedCategory === cat.name ? null : cat.name)
                }
                onProductClick={(query) => {
                  setExpandedCategory(null);
                  onSearch(query);
                }}
              />
            ))}
          </div>
        </Section>
      )}
    </div>
  );
}

/* ── Sub-components ── */

function Section({
  icon,
  label,
  children,
}: {
  icon: React.ReactNode;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-center gap-2 text-xs font-semibold text-gray-400 uppercase tracking-widest">
        {icon}
        <span>{label}</span>
      </div>
      {children}
    </div>
  );
}

function SearchPill({ item, onClick }: { item: TrendingItem; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="group flex items-center gap-2.5 px-4 py-2 rounded-full bg-white/60 hover:bg-white text-gray-700 text-sm font-medium border border-gray-200/50 shadow-sm transition-all hover:shadow-md hover:text-gray-900 backdrop-blur-sm"
    >
      <span>{item.query}</span>
      {item.score != null && (
        <span className="flex items-center gap-1 text-xs font-semibold text-brand-700 bg-brand-50 px-2 py-0.5 rounded-full">
          <Star className="w-3 h-3 fill-brand-500 text-brand-500" />
          {item.score}
        </span>
      )}
      {item.category && (
        <span className="text-xs text-gray-400 group-hover:text-gray-500 hidden sm:inline">
          {item.category}
        </span>
      )}
    </button>
  );
}

function CategoryPill({
  category,
  isExpanded,
  onToggle,
  onProductClick,
}: {
  category: CategoryItem;
  isExpanded: boolean;
  onToggle: () => void;
  onProductClick: (query: string) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!isExpanded) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onToggle();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [isExpanded, onToggle]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={onToggle}
        className={`group flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium border shadow-sm transition-all backdrop-blur-sm ${
          isExpanded
            ? 'bg-white text-gray-900 border-gray-300 shadow-md ring-2 ring-gray-200'
            : 'bg-white/60 hover:bg-white text-gray-600 border-gray-200/50 hover:shadow-md hover:text-gray-900'
        }`}
      >
        <span>{category.name}</span>
        <span className="text-xs text-gray-400 tabular-nums">{category.count}</span>
        <ChevronDown
          className={`w-3.5 h-3.5 text-gray-400 transition-transform duration-200 ${
            isExpanded ? 'rotate-180' : ''
          }`}
        />
      </button>

      {/* Popover dropdown */}
      {isExpanded && category.products.length > 0 && (
        <div className="absolute top-full left-1/2 -translate-x-1/2 mt-2 z-50 min-w-[200px] max-w-[280px] bg-white rounded-xl border border-gray-200 shadow-xl overflow-hidden animate-slide-down">
          <div className="px-3 py-2 border-b border-gray-100">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
              {category.name} Products
            </p>
          </div>
          <ul className="py-1 max-h-[240px] overflow-y-auto">
            {category.products.map((product) => (
              <li key={product}>
                <button
                  onClick={() => onProductClick(product)}
                  className="w-full flex items-center gap-2.5 px-3 py-2.5 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900 transition-colors text-left"
                >
                  <Search className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                  <span className="truncate">{product}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

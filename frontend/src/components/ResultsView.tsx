import { SearchResponse } from '@/lib/api';
import VerdictCard from './VerdictCard';
import CategoryCard from './CategoryCard';
import TestimonialCard from './TestimonialCard';
import PersonaSection from './PersonaSection';

interface ResultsViewProps {
  results: SearchResponse;
}

export default function ResultsView({ results }: ResultsViewProps) {
  return (
    <div className="w-full max-w-5xl mx-auto space-y-8 pb-16 animate-fade-in">
      
      {/* Top Banner: The main verdict inspired by tally.shop */}
      <VerdictCard response={results} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
        
        {/* Main Left Column (Categories & Personas) */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* Categories Grid */}
          <div className="space-y-4">
            <h3 className="text-xl font-bold text-gray-900 border-b border-gray-100 pb-2">
              Performance Breakdown
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {results.categories?.map((cat, i) => (
                <CategoryCard key={i} category={cat} />
              ))}
            </div>
          </div>

          {/* Persona Analysis */}
          <PersonaSection analysis={results.personaAnalysis} />
          
        </div>

        {/* Right Sidebar (Testimonials) */}
        <div className="space-y-4">
          <div className="sticky top-24 space-y-4">
            <h3 className="text-xl font-bold text-gray-900 border-b border-gray-100 pb-2">
              What People Are Saying
            </h3>
            <div className="space-y-4 max-h-[80vh] overflow-y-auto pr-2 pb-4 scrollbar-thin">
              {results.testimonials?.map((t, i) => (
                <TestimonialCard key={i} testimonial={t} />
              ))}
              
              {(!results.testimonials || results.testimonials.length === 0) && (
                <div className="text-gray-500 text-sm italic p-4 bg-gray-50 rounded-xl">
                  No verified testimonials found for this product.
                </div>
              )}
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}

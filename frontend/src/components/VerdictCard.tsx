import { SearchResponse } from '@/lib/api';
import ScoreCircle from './ScoreCircle';
import { Zap } from 'lucide-react';

interface VerdictCardProps {
  response: SearchResponse;
}

export default function VerdictCard({ response }: VerdictCardProps) {
  return (
    <div className="bg-white rounded-[2rem] p-8 md:p-10 shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-gray-100 relative overflow-hidden">
      {/* Decorative subtle grid background */}
      <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGNpcmNsZSBjeD0iMSIgY3k9IjEiIHI9IjEiIGZpbGw9InJnYmEoMCwwLDAsMC4wNSkiLz48L3N2Zz4=')] [mask-image:linear-gradient(to_bottom,white,transparent)] opacity-50 pointer-events-none"></div>
      
      <div className="relative z-10 flex flex-col md:flex-row items-center md:items-start gap-8 md:gap-12">
        {/* Left Side: Score & Verdict */}
        <div className="flex flex-col items-center flex-shrink-0 space-y-4">
          <ScoreCircle score={response.overallScore} />
          <div className="text-center">
            <span className="text-sm font-bold uppercase tracking-widest text-gray-400 mb-1 block">Our Verdict</span>
            <span className="text-2xl font-extrabold text-gray-900">{response.overallVerdict}</span>
          </div>
        </div>

        {/* Right Side: Details */}
        <div className="flex-1 flex flex-col justify-center h-full pt-2">
          <div className="flex flex-wrap items-center gap-2 mb-4">
             {response.sourcePlatforms.map(platform => (
               <span key={platform} className="px-3 py-1 bg-gray-100 text-gray-600 rounded-full text-xs font-semibold capitalize tracking-wide flex items-center">
                 {platform === 'reddit' ? (
                   <span className="w-2 h-2 rounded-full bg-orange-500 mr-2"></span>
                 ) : (
                   <span className="w-2 h-2 rounded-full bg-blue-500 mr-2"></span>
                 )}
                 {platform}
               </span>
             ))}
             <span className="px-3 py-1 bg-brand-50 text-brand-700 rounded-full text-xs font-semibold tracking-wide">
               Parsed from {response.postCount} detailed discussions
             </span>
             {response.cached && (
               <span className="px-3 py-1 bg-yellow-50 text-yellow-700 rounded-full text-xs font-semibold tracking-wide flex items-center" title="Served from lightning-fast cache">
                 <Zap className="w-3 h-3 mr-1 fill-current" />
                 Cached
               </span>
             )}
          </div>
          
          <h2 className="text-4xl font-extrabold text-gray-900 mb-6 leading-tight">
            {response.query}
          </h2>
          
          <p className="text-gray-700 text-lg leading-relaxed font-medium bg-gray-50 p-6 rounded-2xl border border-gray-100 border-l-4 border-l-brand-500">
            {response.verdictSummary}
          </p>
        </div>
      </div>
    </div>
  );
}

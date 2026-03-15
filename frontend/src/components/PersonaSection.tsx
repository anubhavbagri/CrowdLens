import { PersonaAnalysis } from '@/lib/api';
import { Users, CheckCircle, XCircle, MinusCircle } from 'lucide-react';

interface PersonaSectionProps {
  analysis: PersonaAnalysis;
}

export default function PersonaSection({ analysis }: PersonaSectionProps) {
  if (!analysis || !analysis.fits || analysis.fits.length === 0) return null;

  return (
    <div className="bg-gradient-to-br from-gray-900 to-gray-800 rounded-3xl p-8 text-white shadow-xl relative overflow-hidden">
      {/* Abstract background shapes */}
      <div className="absolute top-0 right-0 -mt-20 -mr-20 w-64 h-64 rounded-full bg-brand-500 opacity-10 blur-3xl pointer-events-none"></div>
      <div className="absolute bottom-0 left-0 -mb-20 -ml-20 w-64 h-64 rounded-full bg-blue-500 opacity-10 blur-3xl pointer-events-none"></div>
      
      <div className="relative z-10">
        <h3 className="text-2xl font-bold mb-8 flex items-center">
          <Users className="w-6 h-6 mr-3 text-brand-400" />
          {analysis.question || "Is this right for you?"}
        </h3>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {analysis.fits.map((fit, idx) => {
            const isGoodFit = fit.verdict.toLowerCase().includes('yes') || fit.verdict.toLowerCase().includes('good');
            const isBadFit = fit.verdict.toLowerCase().includes('no') || fit.verdict.toLowerCase().includes('bad');
            
            let icon = <MinusCircle className="w-5 h-5 text-gray-400" />;
            
            if (isGoodFit) {
              icon = <CheckCircle className="w-5 h-5 text-green-400" />;
            } else if (isBadFit) {
              icon = <XCircle className="w-5 h-5 text-red-400" />;
            }

            return (
              <div key={idx} className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10 hover:bg-white/15 transition-colors">
                <div className="flex items-start justify-between mb-3">
                  <h4 className="font-bold text-lg text-white leading-tight">{fit.persona}</h4>
                  <div className="flex-shrink-0 ml-3 bg-white/10 p-1.5 rounded-full">
                    {icon}
                  </div>
                </div>
                <div className="text-sm font-semibold mb-2 text-gray-300 uppercase tracking-widest text-[10px]">
                  {fit.verdict}
                </div>
                <p className="text-gray-300 text-sm leading-relaxed">
                  {fit.reason}
                </p>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

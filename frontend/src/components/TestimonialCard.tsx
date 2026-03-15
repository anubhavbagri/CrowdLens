import { Testimonial } from '@/lib/api';
import { ExternalLink } from 'lucide-react';

interface TestimonialCardProps {
  testimonial: Testimonial;
}

export default function TestimonialCard({ testimonial }: TestimonialCardProps) {
  const isPositive = testimonial.sentiment.toLowerCase() === 'positive';
  const isNegative = testimonial.sentiment.toLowerCase() === 'negative';
  
  let borderColor = "border-gray-300";
  let badgeColor = "bg-gray-100 text-gray-600";
  
  if (isPositive) {
    borderColor = "border-green-400";
    badgeColor = "bg-green-100 text-green-700";
  } else if (isNegative) {
    borderColor = "border-red-400";
    badgeColor = "bg-red-100 text-red-700";
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 hover:shadow-md transition-all duration-300">
      <div className={`pl-4 border-l-4 ${borderColor}`}>
        <p className="text-gray-700 text-sm italic leading-relaxed mb-4">
          "{testimonial.text}"
        </p>
        <div className="flex items-center justify-between mt-auto">
          <div className="flex items-center space-x-2">
            {/* Simple dot for platform */}
            <div className={`w-2 h-2 rounded-full ${testimonial.platform.toLowerCase() === 'reddit' ? 'bg-orange-500' : 'bg-blue-500'}`}></div>
            <span className="text-xs font-semibold text-gray-500">
              {testimonial.source || testimonial.platform}
            </span>
          </div>
          <div className="flex items-center space-x-3">
             <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${badgeColor}`}>
              {testimonial.sentiment}
            </span>
            {testimonial.permalink && (
              <a 
                href={testimonial.permalink} 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-gray-400 hover:text-brand-600 transition-colors"
                title="View original post"
              >
                <ExternalLink className="w-4 h-4" />
              </a>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

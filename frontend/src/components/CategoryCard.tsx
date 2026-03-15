import { CategoryAnalysis } from '@/lib/api';
import { Check } from 'lucide-react';

interface CategoryCardProps {
  category: CategoryAnalysis;
}

export default function CategoryCard({ category }: CategoryCardProps) {
  // Determine color and percentage based on rating
  let colorClass = "bg-green-500";
  let badgeBg = "bg-green-100 text-green-800 border-green-200";
  let percent = "90%";
  
  const ratingLower = category.rating.toLowerCase();
  if (ratingLower.includes('poor') || ratingLower.includes('bad')) {
    colorClass = "bg-red-500";
    badgeBg = "bg-red-100 text-red-800 border-red-200";
    percent = "25%";
  } else if (ratingLower.includes('fair') || ratingLower.includes('average')) {
    colorClass = "bg-yellow-500";
    badgeBg = "bg-yellow-100 text-yellow-800 border-yellow-200";
    percent = "50%";
  } else if (ratingLower.includes('good')) {
    colorClass = "bg-brand-400";
    badgeBg = "bg-brand-50 text-brand-700 border-brand-200";
    percent = "75%";
  } else if (ratingLower.includes('excellent') || ratingLower.includes('great')) {
    colorClass = "bg-brand-600";
    badgeBg = "bg-brand-100 text-brand-900 border-brand-300";
    percent = "95%";
  }

  return (
    <div className="bg-white rounded-2xl p-6 shadow-sm border border-gray-100 hover:shadow-md transition-shadow duration-300">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xl font-bold text-gray-900">{category.name}</h3>
        <span className={`px-3 py-1 rounded-full text-xs font-semibold border uppercase tracking-wider ${badgeBg}`}>
          {category.rating}
        </span>
      </div>
      
      {/* Rating Bar */}
      <div className="w-full bg-gray-100 rounded-full h-2 mb-6 overflow-hidden">
        <div 
          className={`h-2 rounded-full ${colorClass} transition-all duration-1000 ease-out`} 
          style={{ width: percent }}
        ></div>
      </div>

      <p className="text-gray-700 text-sm leading-relaxed mb-6 font-medium">
        {category.summary}
      </p>

      {category.highlights && category.highlights.length > 0 && (
        <div className="space-y-3">
          <h4 className="text-xs uppercase tracking-wider text-gray-500 font-bold">Highlights</h4>
          <ul className="space-y-2">
            {category.highlights.map((highlight, idx) => (
              <li key={idx} className="flex items-start">
                <Check className={`w-5 h-5 mr-3 flex-shrink-0 mt-0.5 ${colorClass.replace('bg-', 'text-')}`} />
                <span className="text-sm text-gray-600 leading-snug">{highlight}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

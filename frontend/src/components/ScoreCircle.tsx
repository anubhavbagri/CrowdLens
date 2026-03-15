'use client';

import { useEffect, useState } from 'react';

interface ScoreCircleProps {
  score: number;
}

export default function ScoreCircle({ score }: ScoreCircleProps) {
  const [percent, setPercent] = useState(0);

  useEffect(() => {
    // Trigger animation after mount
    const timeout = setTimeout(() => setPercent(score), 100);
    return () => clearTimeout(timeout);
  }, [score]);

  // Determine color based on score
  let colorClass = "text-green-500";
  let strokeClass = "stroke-green-500";
  let bgClass = "bg-green-50 text-green-700";
  
  if (score < 50) {
    colorClass = "text-red-500";
    strokeClass = "stroke-red-500";
    bgClass = "bg-red-50 text-red-700";
  } else if (score < 75) {
    colorClass = "text-yellow-500";
    strokeClass = "stroke-yellow-500";
    bgClass = "bg-yellow-50 text-yellow-700";
  } else if (score >= 90) {
    colorClass = "text-brand-500";
    strokeClass = "stroke-brand-500";
    bgClass = "bg-brand-50 text-brand-700";
  }

  const radius = 46;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (percent / 100) * circumference;

  return (
    <div className="relative flex items-center justify-center group transform transition-transform hover:scale-105">
      {/* The wavy blob background behind the circle (tally.shop style) */}
      <div className={`absolute inset-0 rounded-full blur-md opacity-30 ${colorClass.replace('text-', 'bg-')}`}></div>
      
      <svg className="w-32 h-32 transform -rotate-90 relative z-10" viewBox="0 0 100 100">
        <circle
          className="stroke-gray-100"
          strokeWidth="6"
          fill="white"
          r={radius}
          cx="50"
          cy="50"
        />
        <circle
          className={`${strokeClass} transition-all duration-1000 ease-out`}
          strokeWidth="6"
          strokeLinecap="round"
          fill="transparent"
          r={radius}
          cx="50"
          cy="50"
          style={{
            strokeDasharray: circumference,
            strokeDashoffset: strokeDashoffset,
          }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center z-20">
        <span className={`text-3xl font-black tracking-tighter ${colorClass.replace('text-', 'text-gray-900')}`}>
          {percent}
        </span>
      </div>
    </div>
  );
}

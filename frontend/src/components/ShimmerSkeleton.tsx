import { Loader2 } from 'lucide-react';
import { JobStatus } from '@/lib/api';

interface ShimmerResultsProps {
  jobStatus: JobStatus | null;
  elapsedSeconds: number;
}

type Step = { label: string; sub: string; startsAt: number };

const STEPS: Step[] = [
  {
    label: 'Collecting Reddit discussions',
    sub: 'Searching through posts and comments...',
    startsAt: 0,
  },
  {
    label: 'Running AI analysis',
    sub: 'Analyzing sentiment and patterns...',
    startsAt: 8,
  },
  {
    label: 'Building your report',
    sub: 'Almost done — putting the insights together...',
    startsAt: 22,
  },
];

function getStepIndex(elapsed: number): number {
  for (let i = STEPS.length - 1; i >= 0; i--) {
    if (elapsed >= STEPS[i].startsAt) return i;
  }
  return 0;
}

function StatusHeader({ jobStatus, elapsedSeconds }: ShimmerResultsProps) {
  const isPending = jobStatus === null || jobStatus === 'PENDING';
  const stepIndex = isPending ? -1 : getStepIndex(elapsedSeconds);
  const step = stepIndex >= 0 ? STEPS[stepIndex] : null;
  const showElapsed = elapsedSeconds >= 3 && !isPending;

  const label = isPending
    ? jobStatus === 'PENDING'
      ? 'Analysis queued'
      : 'Starting analysis...'
    : step!.label;

  const sub = isPending
    ? jobStatus === 'PENDING'
      ? 'Waiting for the current job to finish...'
      : ''
    : step!.sub;

  return (
    <div className="w-full max-w-5xl mx-auto mb-8">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-5">
        {/* Top row: spinner + label + elapsed */}
        <div className="flex items-center gap-4">
          <div className="w-9 h-9 rounded-full bg-brand-50 flex items-center justify-center flex-shrink-0">
            <Loader2 className="w-5 h-5 text-brand-600 animate-spin" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-gray-800 truncate">{label}</p>
            {sub && <p className="text-sm text-gray-500 mt-0.5">{sub}</p>}
          </div>
          {showElapsed && (
            <span className="text-sm text-gray-400 font-mono flex-shrink-0">{elapsedSeconds}s</span>
          )}
        </div>

        {/* 3-segment progress bar */}
        <div className="flex gap-1.5 mt-4">
          {STEPS.map((_, i) => {
            const isActive = stepIndex === i;
            const isDone = stepIndex > i;
            const isLastStep = i === STEPS.length - 1;
            return (
              <div
                key={i}
                className={`h-1.5 flex-1 rounded-full transition-all duration-700 ${
                  isPending
                    ? i === 0
                      ? 'bg-gray-200 animate-pulse'
                      : 'bg-gray-100'
                    : isDone
                    ? 'bg-brand-500'
                    : isActive
                    ? isLastStep
                      ? 'bg-brand-400 animate-pulse'
                      : 'bg-brand-500'
                    : 'bg-gray-100'
                }`}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

export function ShimmerResults({ jobStatus, elapsedSeconds }: ShimmerResultsProps) {
  return (
    <div className="w-full max-w-5xl mx-auto animate-fade-in">
      <StatusHeader jobStatus={jobStatus} elapsedSeconds={elapsedSeconds} />

      <div className="space-y-8">
        {/* Top Section: Score & Verdict */}
        <div className="flex flex-col md:flex-row gap-8 bg-white p-8 rounded-3xl shadow-sm border border-gray-100">
          <div className="flex-shrink-0 flex items-center justify-center">
            <div className="w-32 h-32 rounded-full skeleton-shimmer"></div>
          </div>
          <div className="flex-1 space-y-4 py-2">
            <div className="h-4 w-24 rounded-full skeleton-shimmer mb-2"></div>
            <div className="h-8 w-3/4 rounded-lg skeleton-shimmer"></div>
            <div className="space-y-2 pt-4">
              <div className="h-4 w-full rounded-md skeleton-shimmer"></div>
              <div className="h-4 w-5/6 rounded-md skeleton-shimmer"></div>
              <div className="h-4 w-4/6 rounded-md skeleton-shimmer"></div>
            </div>
          </div>
        </div>

        {/* Two Column Layout for the rest */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">

          {/* Main Content (Categories + Personas) */}
          <div className="lg:col-span-2 space-y-6">
            <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 h-64 skeleton-shimmer opacity-80"></div>
            <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 h-80 skeleton-shimmer opacity-80"></div>
          </div>

          {/* Sidebar (Testimonials) */}
          <div className="space-y-4">
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 h-32 skeleton-shimmer opacity-80"></div>
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 h-40 skeleton-shimmer opacity-80"></div>
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 h-48 skeleton-shimmer opacity-80"></div>
          </div>

        </div>
      </div>
    </div>
  );
}

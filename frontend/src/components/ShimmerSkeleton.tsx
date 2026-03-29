export function ShimmerResults() {
  return (
    <div className="w-full max-w-5xl mx-auto space-y-8 animate-fade-in">
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
  );
}

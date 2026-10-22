import { Skeleton } from "@/components/ui/skeleton"

export default function StoreLoading() {
  return (
    <div className="container-store py-12 sm:py-16" aria-busy="true">
      <span className="sr-only">Loading page</span>
      <Skeleton className="h-3 w-28" />
      <Skeleton className="mt-6 h-10 w-72 max-w-full" />
      <Skeleton lines={2} className="mt-4 max-w-xl" />
      <div className="mt-12 grid gap-8 lg:grid-cols-[14rem_minmax(0,1fr)]">
        <div className="hidden space-y-4 lg:block">
          <Skeleton className="h-5 w-20" />
          <Skeleton lines={4} />
        </div>
        <div className="grid grid-cols-2 gap-x-4 gap-y-10 sm:grid-cols-3">
          {Array.from({ length: 6 }, (_, index) => (
            <div key={index}>
              <Skeleton className="aspect-[4/5] w-full" />
              <Skeleton className="mt-4 h-3 w-20" />
              <Skeleton className="mt-2 h-4 w-3/4" />
              <Skeleton className="mt-2 h-4 w-24" />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

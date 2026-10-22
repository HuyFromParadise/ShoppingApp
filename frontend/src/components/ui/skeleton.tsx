import type * as React from "react"
import { cn } from "@/lib/utils/cn"

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Render several stacked lines instead of one block. */
  lines?: number
}

/** Loading placeholder. Hidden from assistive tech; wrap with a live status if needed. */
export function Skeleton({ className, lines = 1, ...props }: SkeletonProps) {
  if (lines > 1) {
    return (
      <span className="block space-y-2" aria-hidden="true">
        {Array.from({ length: lines }, (_, index) => (
          <span
            key={index}
            className={cn(
              "block h-4 animate-fade-in rounded-sm bg-[#edf0eb]",
              index === lines - 1 && "w-4/5",
              className,
            )}
          />
        ))}
      </span>
    )
  }

  return (
    <div
      aria-hidden="true"
      className={cn("h-4 animate-fade-in rounded-sm bg-[#edf0eb]", className)}
      {...props}
    />
  )
}

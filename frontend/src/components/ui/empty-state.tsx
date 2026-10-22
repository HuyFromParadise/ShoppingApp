import type * as React from "react"
import { cn } from "@/lib/utils/cn"

export interface EmptyStateProps {
  title: string
  description?: string
  action?: React.ReactNode
  className?: string
}

export function EmptyState({
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center border border-dashed border-line bg-surface px-6 py-16 text-center",
        className,
      )}
    >
      <h2 className="text-lg font-medium tracking-tight text-ink">{title}</h2>
      {description ? (
        <p className="mt-2 max-w-md text-sm text-muted">{description}</p>
      ) : null}
      {action ? <div className="mt-6">{action}</div> : null}
    </div>
  )
}

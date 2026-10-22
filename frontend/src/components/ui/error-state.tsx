"use client"

import type * as React from "react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

export interface ErrorStateProps {
  title?: string
  description?: string
  onRetry?: () => void
  retryLabel?: string
  action?: React.ReactNode
  className?: string
}

export function ErrorState({
  title = "Something went wrong",
  description = "We could not load this content. Please try again.",
  onRetry,
  retryLabel = "Try again",
  action,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-start border border-line bg-surface px-6 py-8",
        className,
      )}
    >
      <h2 className="text-lg font-medium tracking-tight text-ink">{title}</h2>
      <p className="mt-2 max-w-lg text-sm text-muted">{description}</p>
      {onRetry || action ? (
        <div className="mt-5 flex items-center gap-3">
          {onRetry ? (
            <Button variant="secondary" size="sm" onClick={onRetry}>
              {retryLabel}
            </Button>
          ) : null}
          {action}
        </div>
      ) : null}
    </div>
  )
}

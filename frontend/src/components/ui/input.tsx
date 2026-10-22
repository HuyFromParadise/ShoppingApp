import * as React from "react"
import { cn } from "@/lib/utils/cn"

export type InputProps = React.InputHTMLAttributes<HTMLInputElement>

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  function Input({ className, type = "text", ...props }, ref) {
    return (
      <input
        ref={ref}
        type={type}
        className={cn(
          "flex h-10 w-full rounded border border-line bg-surface px-3 py-2 text-sm text-ink transition-colors duration-150 placeholder:text-muted focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-paper disabled:cursor-not-allowed disabled:bg-[#f5f3f0] disabled:opacity-60 aria-[invalid=true]:border-accent",
          className,
        )}
        {...props}
      />
    )
  },
)

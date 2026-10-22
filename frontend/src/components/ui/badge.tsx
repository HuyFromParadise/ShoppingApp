import * as React from "react"
import { cn } from "@/lib/utils/cn"

export type BadgeVariant = "neutral" | "outline" | "accent" | "success" | "warning"

const variantClasses: Record<BadgeVariant, string> = {
  neutral: "border-[#e3dfd9] bg-[#f1efec] text-ink",
  outline: "border-line bg-transparent text-ink",
  accent: "border-[#e7c9c9] bg-[#f6e9e9] text-accent",
  success: "border-[#c9dfd3] bg-[#e8f1ec] text-success",
  warning: "border-[#e8dcc0] bg-[#f8f1de] text-[#7a5a12]",
}

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant
}

export function Badge({
  className,
  variant = "neutral",
  ...props
}: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-sm border px-2 py-0.5 text-xs font-medium uppercase tracking-label",
        variantClasses[variant],
        className,
      )}
      {...props}
    />
  )
}

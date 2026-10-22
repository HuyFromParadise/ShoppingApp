import * as React from "react"
import { cn } from "@/lib/utils/cn"

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "ghost"
  | "destructive"
  | "link"
export type ButtonSize = "sm" | "md" | "lg"

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    "border border-ink bg-ink text-surface hover:bg-[#2b2b2b] hover:border-[#2b2b2b]",
  secondary:
    "border border-line bg-surface text-ink hover:border-ink",
  ghost: "border border-transparent bg-transparent text-ink hover:bg-[#f0ede8]",
  destructive:
    "border border-accent bg-accent text-surface hover:bg-[#741717] hover:border-[#741717]",
  link: "border-0 bg-transparent px-0 text-ink underline underline-offset-4 hover:text-accent",
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-sm",
  md: "h-10 px-5 text-sm",
  lg: "h-12 px-7 text-base",
}

export interface ButtonVariantOptions {
  variant?: ButtonVariant
  size?: ButtonSize
  className?: string
}

export function buttonVariants({
  variant = "primary",
  size = "md",
  className,
}: ButtonVariantOptions = {}): string {
  return cn(
    "inline-flex items-center justify-center gap-2 rounded font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-paper disabled:pointer-events-none disabled:opacity-50",
    variantClasses[variant],
    variant === "link" ? "h-auto" : sizeClasses[size],
    className,
  )
}

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  function Button(
    {
      className,
      type = "button",
      variant = "primary",
      size = "md",
      loading = false,
      disabled,
      children,
      ...props
    },
    ref,
  ) {
    return (
      <button
        ref={ref}
        type={type}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        className={buttonVariants({ variant, size, className })}
        {...props}
      >
        {loading ? (
          <span
            aria-hidden="true"
            className="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent"
          />
        ) : null}
        {children}
      </button>
    )
  },
)

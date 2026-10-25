import * as React from "react"
import { cn } from "@/lib/utils/cn"
import { Input } from "@/components/ui/input"

/** A labelled native select, matching the Input styling. */
export interface SelectFieldProps
  extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  hint?: string
  error?: string
  options: ReadonlyArray<{ value: string; label: string }>
  /** Adds an empty first option, e.g. "All statuses". */
  placeholder?: string
}

export const SelectField = React.forwardRef<HTMLSelectElement, SelectFieldProps>(
  function SelectField(
    { label, hint, error, options, placeholder, className, id, ...props },
    ref,
  ) {
    const generatedId = React.useId()
  const fieldId = id ?? generatedId
    const hintId = hint ? `${fieldId}-hint` : undefined
    const errorId = error ? `${fieldId}-error` : undefined
    return (
      <div className="space-y-1.5">
        <label htmlFor={fieldId} className="block text-sm font-medium text-ink">
          {label}
        </label>
        <select
          ref={ref}
          id={fieldId}
          aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
          aria-invalid={error ? true : undefined}
          className={cn(
            "flex h-10 w-full rounded border border-line bg-surface px-3 py-2 text-sm text-ink transition-colors duration-150 focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-paper disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-accent",
            className,
          )}
          {...props}
        >
          {placeholder ? <option value="">{placeholder}</option> : null}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {hint ? (
          <p id={hintId} className="text-xs text-muted">
            {hint}
          </p>
        ) : null}
        {error ? (
          <p id={errorId} className="text-xs text-accent">
            {error}
          </p>
        ) : null}
      </div>
    )
  },
)

/** A labelled multi-line text control for descriptions and notes. */
export interface TextareaFieldProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  hint?: string
  error?: string
}

export const TextareaField = React.forwardRef<
  HTMLTextAreaElement,
  TextareaFieldProps
>(function TextareaField({ label, hint, error, className, id, ...props }, ref) {
  const generatedId = React.useId()
  const fieldId = id ?? generatedId
  const hintId = hint ? `${fieldId}-hint` : undefined
  const errorId = error ? `${fieldId}-error` : undefined
  return (
    <div className="space-y-1.5">
      <label htmlFor={fieldId} className="block text-sm font-medium text-ink">
        {label}
      </label>
      <textarea
        ref={ref}
        id={fieldId}
        aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
        aria-invalid={error ? true : undefined}
        className={cn(
          "flex w-full rounded border border-line bg-surface px-3 py-2 text-sm text-ink transition-colors duration-150 focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-paper disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-accent",
          className,
        )}
        {...props}
      />
      {hint ? (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-xs text-accent">
          {error}
        </p>
      ) : null}
    </div>
  )
})

/** A labelled checkbox that stays a single tap target on mobile. */
export function CheckboxField({
  label,
  hint,
  id,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & {
  label: string
  hint?: string
}) {
  const generatedId = React.useId()
  const fieldId = id ?? generatedId
  return (
    <div className="flex items-start gap-3">
      <input
        id={fieldId}
        type="checkbox"
        className="mt-0.5 h-4 w-4 accent-[var(--color-ink)]"
        {...props}
      />
      <label htmlFor={fieldId} className="text-sm text-ink">
        {label}
        {hint ? <span className="mt-0.5 block text-xs text-muted">{hint}</span> : null}
      </label>
    </div>
  )
}

/** Money as integer minor units, entered with digits only (ADR-008). */
export interface MoneyFieldProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "onChange" | "value" | "type"> {
  label: string
  hint?: string
  error?: string
  valueMinor: number | null
  onChangeMinor: (value: number | null) => void
  currency?: string
}

export function MoneyField({
  label,
  hint,
  error,
  valueMinor,
  onChangeMinor,
  currency = "VND",
  id,
  ...props
}: MoneyFieldProps) {
  const generatedId = React.useId()
  const fieldId = id ?? generatedId
  const hintId = hint ? `${fieldId}-hint` : undefined
  const errorId = error ? `${fieldId}-error` : undefined
  return (
    <div className="space-y-1.5">
      <label htmlFor={fieldId} className="block text-sm font-medium text-ink">
        {label}
        <span className="ml-1 text-xs font-normal text-muted">{currency}</span>
      </label>
      <Input
        id={fieldId}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={valueMinor === null ? "" : String(valueMinor)}
        onChange={(event) => {
          // Digits only: the API takes integer minor units, never "499.000".
          const digits = event.target.value.replace(/[^\d]/g, "")
          onChangeMinor(digits === "" ? null : Number(digits))
        }}
        aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
        aria-invalid={error ? true : undefined}
        {...props}
      />
      {hint ? (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-xs text-accent">
          {error}
        </p>
      ) : null}
    </div>
  )
}

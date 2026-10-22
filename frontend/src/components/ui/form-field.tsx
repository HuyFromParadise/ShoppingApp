"use client"

import * as React from "react"
import { cn } from "@/lib/utils/cn"

type ControlProps = {
  id?: string
  "aria-describedby"?: string
  "aria-invalid"?: boolean
  "aria-required"?: boolean
}

export interface FormFieldProps {
  id: string
  label: string
  hint?: string
  error?: string
  required?: boolean
  className?: string
  /** A single form control; the field wires id and aria attributes into it. */
  children: React.ReactElement<ControlProps>
}

/**
 * Accessible label + hint + error wrapper. The error id is linked through
 * `aria-describedby` so assistive technology reads the message with the control.
 */
export function FormField({
  id,
  label,
  hint,
  error,
  required = false,
  className,
  children,
}: FormFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy =
    [hintId, errorId].filter(Boolean).join(" ") || undefined

  const control = React.cloneElement(children, {
    id: children.props.id ?? id,
    "aria-describedby":
      [children.props["aria-describedby"], describedBy]
        .filter(Boolean)
        .join(" ") || undefined,
    "aria-invalid": error ? true : children.props["aria-invalid"],
    "aria-required": required || undefined,
  })

  return (
    <div className={cn("space-y-1.5", className)}>
      <label
        htmlFor={id}
        className="block text-sm font-medium text-ink"
      >
        {label}
        {required ? (
          <span className="ml-0.5 text-accent" aria-hidden="true">
            *
          </span>
        ) : null}
      </label>
      {hint ? (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      ) : null}
      {control}
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
    </div>
  )
}

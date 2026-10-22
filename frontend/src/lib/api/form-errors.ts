import { ApiError } from "@/lib/api/client"
import type { FieldValues, Path, UseFormSetError } from "react-hook-form"

/**
 * Maps a backend error envelope onto form fields. A field error is attached to
 * its input; an error for a field the form does not have (or any error without
 * field errors) becomes one form-level message.
 */
export function applyServerError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: ReadonlyArray<Path<T>>,
): string {
  if (error instanceof ApiError) {
    const fieldErrors = error.fieldErrors ?? {}
    const unattached: string[] = []
    let attached = false
    for (const [field, message] of Object.entries(fieldErrors)) {
      if (!fields.includes(field as Path<T>)) {
        unattached.push(message)
        continue
      }
      setError(field as Path<T>, { type: "server", message })
      attached = true
    }
    if (unattached.length > 0) return unattached.join(" ")
    if (attached) return ""
    return error.message
  }
  return "Something went wrong. Please try again."
}

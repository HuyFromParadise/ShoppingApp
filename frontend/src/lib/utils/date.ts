const LOCALE = "vi-VN"

function parseDate(iso: string): Date | null {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

/** Formats an ISO-8601 timestamp as a Vietnamese local date (`21/10/2026`). */
export function formatDate(iso: string): string {
  const date = parseDate(iso)
  return date ? date.toLocaleDateString(LOCALE) : "—"
}

/** Formats an ISO-8601 timestamp as a Vietnamese local date and time. */
export function formatDateTime(iso: string): string {
  const date = parseDate(iso)
  if (!date) return "—"
  return new Intl.DateTimeFormat(LOCALE, {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date)
}

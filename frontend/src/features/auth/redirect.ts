/**
 * Keeps post-login redirects inside the app. The value is resolved against a
 * fixed base and only same-origin paths survive, so `?next=/\evil.com`,
 * `?next=//evil.com` and `?next=https://evil.com` cannot turn the login page
 * into an open redirect. Browsers treat `\` as `/` in the authority, so a
 * prefix check alone is not enough.
 */
const BASE_URL = "https://app.invalid"

export function safeRedirectPath(value: unknown): string | undefined {
  const candidate = Array.isArray(value) ? value[0] : value
  if (typeof candidate !== "string" || candidate.length === 0) {
    return undefined
  }
  try {
    const base = new URL(BASE_URL)
    const resolved = new URL(candidate, base)
    if (resolved.origin !== base.origin) {
      return undefined
    }
    return `${resolved.pathname}${resolved.search}${resolved.hash}`
  } catch {
    return undefined
  }
}

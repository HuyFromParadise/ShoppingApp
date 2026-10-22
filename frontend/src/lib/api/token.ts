/**
 * Access token store.
 *
 * The JWT lives in memory only (module scope), never in `localStorage`,
 * `sessionStorage`, or a readable cookie. The auth feature wraps this store in
 * React state during the authentication phase.
 */
type TokenListener = (token: string | null) => void

let accessToken: string | null = null
const listeners = new Set<TokenListener>()

function notify(): void {
  for (const listener of listeners) listener(accessToken)
}

export const tokenStore = {
  get(): string | null {
    return accessToken
  },
  set(token: string | null): void {
    accessToken = token
    notify()
  },
  clear(): void {
    accessToken = null
    notify()
  },
  /** Notifies when the token is replaced or dropped (e.g. a failed refresh). */
  subscribe(listener: TokenListener): () => void {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

/**
 * Access token store.
 *
 * The JWT lives in memory only (module scope), never in `localStorage`,
 * `sessionStorage`, or a readable cookie. The auth feature wraps this store in
 * React state during the authentication phase.
 */
let accessToken: string | null = null

export const tokenStore = {
  get(): string | null {
    return accessToken
  },
  set(token: string | null): void {
    accessToken = token
  },
  clear(): void {
    accessToken = null
  },
}

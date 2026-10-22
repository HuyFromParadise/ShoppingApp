"use client"

import * as React from "react"
import { refreshAccessToken } from "@/lib/api/client"
import { tokenStore } from "@/lib/api/token"
import type { AuthResponse, UserResponse } from "@/types/auth"

export type SessionStatus = "loading" | "authenticated" | "unauthenticated"

export interface SessionState {
  status: SessionStatus
  user: UserResponse | null
}

type Listener = (state: SessionState) => void

const listeners = new Set<Listener>()
let state: SessionState = { status: "loading", user: null }

/**
 * One refresh per page load. The refresh token rotates on every use, so a
 * second concurrent call would look like token theft and revoke the chain.
 * The lock itself lives in the API client, shared with the 401 retry path.
 */
let bootstrapStarted = false

function setState(next: SessionState): void {
  state = next
  for (const listener of listeners) listener(state)
}

export const sessionStore = {
  get(): SessionState {
    return state
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  /** Signs the user in; the access token goes to the in-memory store. */
  establish(auth: AuthResponse): void {
    tokenStore.set(auth.accessToken)
    setState({ status: "authenticated", user: auth.user })
  },
  /** Replaces the signed-in user, e.g. after a profile update. */
  updateUser(user: UserResponse): void {
    setState({ status: "authenticated", user })
  },
  /** Signs the user out locally and forgets the bootstrap. */
  clear(): void {
    bootstrapStarted = false
    tokenStore.clear()
    setState({ status: "unauthenticated", user: null })
  },
  /** Test helper: back to the pre-bootstrap state. */
  reset(): void {
    bootstrapStarted = false
    tokenStore.clear()
    setState({ status: "loading", user: null })
  },
}

// The API client drops the token when a refresh fails. Mirror that in the
// session so the UI cannot keep showing a signed-in shell.
tokenStore.subscribe((token) => {
  if (token === null) setState({ status: "unauthenticated", user: null })
})

const SessionContext = React.createContext<SessionState | null>(null)

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = React.useState<SessionState>(sessionStore.get())

  React.useEffect(() => sessionStore.subscribe(setSession), [])

  React.useEffect(() => {
    let cancelled = false
    if (!bootstrapStarted) {
      bootstrapStarted = true
      void refreshAccessToken().then((auth) => {
        if (cancelled) return
        if (auth) {
          sessionStore.establish(auth)
        } else if (sessionStore.get().status !== "authenticated") {
          // A login that completed while the bootstrap was in flight must not
          // be undone by the failed bootstrap.
          sessionStore.clear()
        }
      })
    }
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <SessionContext.Provider value={session}>{children}</SessionContext.Provider>
  )
}

export function useSession(): SessionState {
  const context = React.useContext(SessionContext)
  if (!context) {
    throw new Error("useSession must be used within a SessionProvider")
  }
  return context
}

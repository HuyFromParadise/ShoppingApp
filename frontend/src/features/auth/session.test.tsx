import { render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { tokenStore } from "@/lib/api/token"
import type { AuthResponse } from "@/types/auth"
import { SessionProvider, sessionStore, useSession } from "./session"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const authPayload: { data: AuthResponse; message: string } = {
  data: {
    accessToken: "access-token",
    user: {
      id: "u1",
      email: "huy@example.com",
      firstName: "Huy",
      lastName: "Pham",
      phone: null,
      role: "CUSTOMER",
      status: "ACTIVE",
    },
  },
  message: "Success",
}

function authResponse(): AuthResponse {
  return authPayload.data
}

function SessionProbe() {
  const { status, user } = useSession()
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{user ? user.email : "none"}</span>
    </div>
  )
}

function renderSession() {
  return render(
    <SessionProvider>
      <SessionProbe />
    </SessionProvider>,
  )
}

/**
 * The jsdom environment used here exposes no `localStorage`, so both storages
 * are replaced with a spy: any write of the access token is then visible.
 */
function stubBrowserStorage() {
  const written = new Map<string, string>()
  const storage = {
    getItem: (key: string) => written.get(key) ?? null,
    setItem: (key: string, value: string) => {
      written.set(key, value)
    },
    removeItem: (key: string) => {
      written.delete(key)
    },
    clear: () => written.clear(),
    key: () => null,
    get length() {
      return written.size
    },
  }
  Object.defineProperty(window, "localStorage", {
    value: storage,
    configurable: true,
  })
  Object.defineProperty(window, "sessionStorage", {
    value: storage,
    configurable: true,
  })
  return written
}

describe("session store", () => {
  beforeEach(() => {
    sessionStore.reset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("keeps the access token in memory only", () => {
    const written = stubBrowserStorage()

    sessionStore.establish(authResponse())

    expect(tokenStore.get()).toBe("access-token")
    expect(written.size).toBe(0)
    expect(sessionStore.get()).toMatchObject({
      status: "authenticated",
      user: { email: "huy@example.com" },
    })
  })

  it("drops the session when the API client discards the token", () => {
    sessionStore.establish(authResponse())

    tokenStore.clear()

    expect(sessionStore.get().status).toBe("unauthenticated")
    expect(sessionStore.get().user).toBeNull()
  })
})

describe("SessionProvider", () => {
  beforeEach(() => {
    sessionStore.reset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("restores the session from the refresh cookie", async () => {
    const mock = vi.fn(() => Promise.resolve(jsonResponse(authPayload)))
    vi.stubGlobal("fetch", mock)

    renderSession()

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated"),
    )
    expect(screen.getByTestId("user")).toHaveTextContent("huy@example.com")
    expect(tokenStore.get()).toBe("access-token")
  })

  it("stays anonymous when there is no refresh cookie", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse(
            {
              status: 401,
              code: "UNAUTHENTICATED",
              message: "Refresh token missing",
            },
            401,
          ),
        ),
      ),
    )

    renderSession()

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"),
    )
    expect(tokenStore.get()).toBeNull()
  })

  it("refreshes only once per page load", async () => {
    const mock = vi.fn(() => Promise.resolve(jsonResponse(authPayload)))
    vi.stubGlobal("fetch", mock)

    renderSession()

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated"),
    )
    expect(mock).toHaveBeenCalledTimes(1)
  })
})

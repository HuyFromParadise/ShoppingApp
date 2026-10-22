import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { tokenStore } from "@/lib/api/token"
import type { AuthResponse } from "@/types/auth"
import { useLogout } from "./hooks"
import { sessionStore } from "./session"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const authPayload: AuthResponse = {
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
}

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe("useLogout", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    sessionStore.reset()
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(new Response(null, { status: 204 })),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("revokes the refresh chain and clears the session", async () => {
    sessionStore.establish(authPayload)
    const { result } = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(String(fetchMock.mock.calls[0][0])).toContain("/auth/logout")
    expect(tokenStore.get()).toBeNull()
    expect(sessionStore.get().status).toBe("unauthenticated")
  })

  it("clears the session even when the logout call fails", async () => {
    sessionStore.establish(authPayload)
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse(
          {
            status: 500,
            code: "INTERNAL_ERROR",
            message: "An unexpected error occurred",
          },
          500,
        ),
      ),
    )
    const { result } = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync().catch(() => undefined)
    })

    await waitFor(() =>
      expect(sessionStore.get().status).toBe("unauthenticated"),
    )
    expect(tokenStore.get()).toBeNull()
  })
})

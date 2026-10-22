import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { tokenStore } from "@/lib/api/token"
import { sessionStore } from "@/features/auth/session"
import type { AuthResponse } from "@/types/auth"
import { ACCOUNT_KEYS, useCreateAddress, useUpdateProfile } from "./hooks"

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

describe("account hooks", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    sessionStore.reset()
    sessionStore.establish(authPayload)
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse({ data: authPayload.user, message: "Success" })),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("keeps the session in sync after a profile update", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse({
          data: { ...authPayload.user, firstName: "Huy Pham", phone: "0900000000" },
          message: "Success",
        }),
      ),
    )
    const { result } = renderHook(() => useUpdateProfile(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync({ firstName: "Huy Pham" })
    })

    expect(sessionStore.get().user?.firstName).toBe("Huy Pham")
    expect(sessionStore.get().status).toBe("authenticated")
    // the access token is untouched by a profile update
    expect(tokenStore.get()).toBe("access-token")
  })

  it("refreshes the address list after creating an address", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidate = vi.spyOn(queryClient, "invalidateQueries")
    const { result } = renderHook(() => useCreateAddress(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    })

    await act(async () => {
      await result.current.mutateAsync({
        recipientName: "An Nguyen",
        addressLine1: "12 Le Loi",
        cityOrProvince: "Ho Chi Minh City",
      })
    })

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ACCOUNT_KEYS.addresses })
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { api, API_BASE_URL, ApiError } from "./client"
import { tokenStore } from "./token"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

type FetchCall = [input: string, init?: RequestInit]

interface FetchMock {
  (input: string, init?: RequestInit): Promise<Response>
  mock: { calls: FetchCall[] }
  mockResolvedValueOnce(value: Response): FetchMock
  mockRejectedValueOnce(value: unknown): FetchMock
}

function stubFetch(...responses: Array<Response | Error>): FetchMock {
  const mock = vi.fn() as unknown as FetchMock
  for (const response of responses) {
    if (response instanceof Error) {
      mock.mockRejectedValueOnce(response)
    } else {
      mock.mockResolvedValueOnce(response)
    }
  }
  vi.stubGlobal("fetch", mock)
  return mock
}

describe("api client", () => {
  beforeEach(() => {
    tokenStore.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("unwraps the success envelope and serializes query params", async () => {
    const mock = stubFetch(
      jsonResponse({ data: { id: "p1" }, message: "Success" }),
    )

    const result = await api.get<{ id: string }>("/products", {
      query: { q: "shirt", page: 0, size: 20, empty: "", missing: undefined },
    })

    expect(result).toEqual({ id: "p1" })
    expect(mock).toHaveBeenCalledTimes(1)
    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/products?q=shirt&page=0&size=20`)
    expect(init?.cache).toBe("no-store")
    expect((init?.headers as Record<string, string>).Authorization).toBeUndefined()
  })

  it("maps the backend error envelope to ApiError", async () => {
    stubFetch(
      jsonResponse(
        {
          timestamp: "2026-10-21T00:00:00Z",
          status: 400,
          code: "VALIDATION_ERROR",
          message: "Request validation failed",
          fieldErrors: { email: "Must be a valid email address" },
          path: "/api/v1/auth/register",
        },
        400,
      ),
    )

    const error = await api
      .post("/auth/register", { email: "nope" })
      .catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      code: "VALIDATION_ERROR",
      message: "Request validation failed",
      fieldErrors: { email: "Must be a valid email address" },
      path: "/api/v1/auth/register",
    })
  })

  it("refreshes once on 401 and retries with the new access token", async () => {
    tokenStore.set("expired-token")
    const mock = stubFetch(
      jsonResponse(
        {
          status: 401,
          code: "UNAUTHENTICATED",
          message: "Authentication required",
        },
        401,
      ),
      jsonResponse({ data: { accessToken: "fresh-token" }, message: "Success" }),
      jsonResponse({ data: { id: "u1" }, message: "Success" }),
    )

    const result = await api.get<{ id: string }>("/account/profile")

    expect(result).toEqual({ id: "u1" })
    expect(mock).toHaveBeenCalledTimes(3)

    const firstHeaders = mock.mock.calls[0][1]?.headers as Record<string, string>
    expect(firstHeaders.Authorization).toBe("Bearer expired-token")

    const [refreshUrl, refreshInit] = mock.mock.calls[1]
    expect(refreshUrl).toBe(`${API_BASE_URL}/auth/refresh`)
    expect(refreshInit?.method).toBe("POST")
    expect(refreshInit?.credentials).toBe("include")

    const retryHeaders = mock.mock.calls[2][1]?.headers as Record<string, string>
    expect(retryHeaders.Authorization).toBe("Bearer fresh-token")
    expect(tokenStore.get()).toBe("fresh-token")
  })

  it("refreshes and retries a 401 on logout so the refresh chain is revoked", async () => {
    tokenStore.set("expired-token")
    const mock = stubFetch(
      jsonResponse(
        { status: 401, code: "UNAUTHENTICATED", message: "Authentication required" },
        401,
      ),
      jsonResponse({ data: { accessToken: "fresh-token" }, message: "Success" }),
      new Response(null, { status: 204 }),
    )

    await api.post("/auth/logout")

    expect(mock).toHaveBeenCalledTimes(3)
    expect(mock.mock.calls[1][0]).toBe(`${API_BASE_URL}/auth/refresh`)
    const retryHeaders = mock.mock.calls[2][1]?.headers as Record<string, string>
    expect(retryHeaders.Authorization).toBe("Bearer fresh-token")
  })

  it("never refreshes after a failed login", async () => {
    const mock = stubFetch(
      jsonResponse(
        { status: 401, code: "UNAUTHENTICATED", message: "Invalid email or password" },
        401,
      ),
    )

    await expect(
      api.post("/auth/login", { email: "huy@example.com", password: "wrong" }),
    ).rejects.toMatchObject({ status: 401 })
    expect(mock).toHaveBeenCalledTimes(1)
  })

  it("shares one refresh between concurrent 401 responses", async () => {
    tokenStore.set("expired-token")
    const mock = stubFetch(
      jsonResponse(
        { status: 401, code: "UNAUTHENTICATED", message: "Authentication required" },
        401,
      ),
      jsonResponse(
        { status: 401, code: "UNAUTHENTICATED", message: "Authentication required" },
        401,
      ),
      jsonResponse({ data: { accessToken: "fresh-token" }, message: "Success" }),
      jsonResponse({ data: { id: "u1" }, message: "Success" }),
      jsonResponse({ data: { id: "u2" }, message: "Success" }),
    )

    const [first, second] = await Promise.all([
      api.get<{ id: string }>("/orders"),
      api.get<{ id: string }>("/cart"),
    ])

    expect(first).toEqual({ id: "u1" })
    expect(second).toEqual({ id: "u2" })
    // 2 rejected calls + 1 refresh + 2 retries
    expect(mock).toHaveBeenCalledTimes(5)
    const refreshCalls = mock.mock.calls.filter(
      ([url]) => url === `${API_BASE_URL}/auth/refresh`,
    )
    expect(refreshCalls).toHaveLength(1)
  })

  it("never attaches the access token to another origin", async () => {
    tokenStore.set("access-token")
    const mock = stubFetch(jsonResponse({ data: { id: "p1" }, message: "Success" }))

    await api.get<{ id: string }>("https://evil.example/steal")

    const headers = mock.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it("does not retry a 401 when the refresh fails", async () => {
    tokenStore.set("expired-token")
    stubFetch(
      jsonResponse(
        { status: 401, code: "UNAUTHENTICATED", message: "Authentication required" },
        401,
      ),
      jsonResponse(
        { status: 401, code: "UNAUTHENTICATED", message: "Refresh failed" },
        401,
      ),
    )

    await expect(api.get("/orders")).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHENTICATED",
    })
    expect(tokenStore.get()).toBeNull()
  })

  it("maps network failures to a network ApiError", async () => {
    stubFetch(new TypeError("Failed to fetch"))

    await expect(api.get("/products")).rejects.toMatchObject({
      status: 0,
      code: "NETWORK_ERROR",
      message: "Unable to reach the server. Check your connection and try again.",
    })
  })

  it("normalizes the paginated envelope", async () => {
    stubFetch(
      jsonResponse({
        data: [{ id: "p1" }, { id: "p2" }],
        page: 1,
        size: 2,
        totalElements: 7,
        totalPages: 4,
        message: "Success",
      }),
    )

    const page = await api.getPage<{ id: string }>("/products", {
      query: { page: 1, size: 2 },
    })

    expect(page).toEqual({
      items: [{ id: "p1" }, { id: "p2" }],
      page: 1,
      size: 2,
      totalElements: 7,
      totalPages: 4,
    })
  })

  it("returns undefined for 204 responses", async () => {
    stubFetch(new Response(null, { status: 204 }))

    await expect(api.delete("/cart/items/1")).resolves.toBeUndefined()
  })
})

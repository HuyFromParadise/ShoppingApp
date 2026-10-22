import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { API_BASE_URL } from "@/lib/api/client"
import { tokenStore } from "@/lib/api/token"
import { login, logout, register } from "./api"

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
}

function stubFetch(...responses: Response[]): FetchMock {
  const mock = vi.fn() as unknown as FetchMock
  for (const response of responses) mock.mockResolvedValueOnce(response)
  vi.stubGlobal("fetch", mock)
  return mock
}

const authPayload = {
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

describe("auth api", () => {
  beforeEach(() => {
    tokenStore.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("posts credentials to the login endpoint and unwraps the envelope", async () => {
    const mock = stubFetch(jsonResponse(authPayload))

    const result = await login({ email: "huy@example.com", password: "secret" })

    expect(result.accessToken).toBe("access-token")
    expect(result.user.email).toBe("huy@example.com")

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/auth/login`)
    expect(init?.method).toBe("POST")
    // The refresh cookie must travel with the request and be stored.
    expect(init?.credentials).toBe("include")
    expect(JSON.parse(String(init?.body))).toEqual({
      email: "huy@example.com",
      password: "secret",
    })
  })

  it("posts credentials to the register endpoint", async () => {
    const mock = stubFetch(jsonResponse(authPayload, 201))

    await register({
      email: "huy@example.com",
      password: "a-long-password",
      firstName: "Huy",
      lastName: "Pham",
    })

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/auth/register`)
    expect(init?.credentials).toBe("include")
  })

  it("logs out with the bearer token and the refresh cookie", async () => {
    tokenStore.set("access-token")
    const mock = stubFetch(new Response(null, { status: 204 }))

    await logout()

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/auth/logout`)
    expect(init?.credentials).toBe("include")
    expect((init?.headers as Record<string, string>).Authorization).toBe(
      "Bearer access-token",
    )
  })

  it("surfaces the backend error envelope", async () => {
    stubFetch(
      jsonResponse(
        {
          timestamp: "2026-10-22T00:00:00Z",
          status: 401,
          code: "UNAUTHENTICATED",
          message: "Invalid email or password",
          path: "/api/v1/auth/login",
        },
        401,
      ),
    )

    await expect(
      login({ email: "huy@example.com", password: "wrong" }),
    ).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHENTICATED",
      message: "Invalid email or password",
    })
  })
})

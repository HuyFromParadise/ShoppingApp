import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { tokenStore } from "@/lib/api/token"
import { LoginForm } from "./login-form"

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
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

function renderForm(next?: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <LoginForm next={next} />
    </QueryClientProvider>,
  )
}

function fillForm(email: string, password: string) {
  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: email } })
  fireEvent.change(screen.getByLabelText(/password/i), {
    target: { value: password },
  })
  fireEvent.click(screen.getByRole("button", { name: /sign in/i }))
}

describe("LoginForm", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    tokenStore.clear()
    mocks.push.mockClear()
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(authPayload)),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("rejects a malformed email before calling the API", async () => {
    renderForm()

    fillForm("not-an-email", "a-long-password")

    expect(
      await screen.findByText("Must be a valid email address"),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("signs in, keeps the token in memory and redirects", async () => {
    renderForm()

    fillForm("  Huy@Example.com ", "a-long-password")

    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith("/"))
    expect(tokenStore.get()).toBe("access-token")

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${"http://localhost:8080/api/v1"}/auth/login`)
    expect(JSON.parse(String(init?.body))).toEqual({
      email: "huy@example.com",
      password: "a-long-password",
    })
  })

  it("returns to the requested page after signing in", async () => {
    renderForm("/account/orders")

    fillForm("huy@example.com", "a-long-password")

    await waitFor(() =>
      expect(mocks.push).toHaveBeenCalledWith("/account/orders"),
    )
  })

  it("shows the backend message when the credentials are wrong", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
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
      ),
    )

    renderForm()

    fillForm("huy@example.com", "wrong-password")

    expect(
      await screen.findByText("Invalid email or password"),
    ).toBeInTheDocument()
    expect(mocks.push).not.toHaveBeenCalled()
    expect(tokenStore.get()).toBeNull()
  })
})

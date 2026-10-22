import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { tokenStore } from "@/lib/api/token"
import { RegisterForm } from "./register-form"

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

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <RegisterForm />
    </QueryClientProvider>,
  )
}

function fillForm(overrides: Partial<Record<string, string>> = {}) {
  const values = {
    email: "huy@example.com",
    password: "a-long-enough-password",
    confirmPassword: "a-long-enough-password",
    firstName: "Huy",
    lastName: "Pham",
    ...overrides,
  }
  fireEvent.change(screen.getByLabelText(/email/i), {
    target: { value: values.email },
  })
  fireEvent.change(screen.getByLabelText(/^password/i), {
    target: { value: values.password },
  })
  fireEvent.change(screen.getByLabelText(/confirm password/i), {
    target: { value: values.confirmPassword },
  })
  fireEvent.change(screen.getByLabelText(/first name/i), {
    target: { value: values.firstName },
  })
  fireEvent.change(screen.getByLabelText(/last name/i), {
    target: { value: values.lastName },
  })
  fireEvent.click(screen.getByRole("button", { name: /create account/i }))
}

describe("RegisterForm", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    tokenStore.clear()
    mocks.push.mockClear()
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(authPayload, 201)),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("rejects a short password before calling the API", async () => {
    renderForm()

    fillForm({
      password: "short",
      confirmPassword: "short",
    })

    expect(
      await screen.findByText("Password must be at least 10 characters"),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("rejects a confirmation that does not match", async () => {
    renderForm()

    fillForm({ confirmPassword: "something-else-entirely" })

    expect(await screen.findByText("Passwords do not match")).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("creates the account, signs the user in and redirects", async () => {
    renderForm()

    fillForm()

    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith("/"))
    expect(tokenStore.get()).toBe("access-token")

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe("http://localhost:8080/api/v1/auth/register")
    expect(JSON.parse(String(init?.body))).toEqual({
      email: "huy@example.com",
      password: "a-long-enough-password",
      firstName: "Huy",
      lastName: "Pham",
    })
  })

  it("shows the backend message when the email is already registered", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse(
          {
            timestamp: "2026-10-22T00:00:00Z",
            status: 409,
            code: "CONFLICT",
            message: "Email already registered",
            path: "/api/v1/auth/register",
          },
          409,
        ),
      ),
    )

    renderForm()

    fillForm()

    expect(
      await screen.findByText("Email already registered"),
    ).toBeInTheDocument()
    expect(mocks.push).not.toHaveBeenCalled()
  })
})

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import { ProfileForm } from "./profile-form"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const profile = {
  id: "u1",
  email: "huy@example.com",
  firstName: "Huy",
  lastName: "Pham",
  phone: "0900000000",
  role: "CUSTOMER" as const,
  status: "ACTIVE" as const,
}

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ProfileForm profile={profile} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("ProfileForm", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse({ data: profile, message: "Success" })),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("shows the profile with the email read-only", () => {
    renderForm()

    expect(screen.getByText("huy@example.com")).toBeInTheDocument()
    expect(screen.getByLabelText(/first name/i)).toHaveValue("Huy")
    expect(screen.getByLabelText(/last name/i)).toHaveValue("Pham")
    expect(screen.getByLabelText(/phone/i)).toHaveValue("0900000000")
  })

  it("rejects a blank name before calling the API", async () => {
    renderForm()

    fireEvent.change(screen.getByLabelText(/first name/i), {
      target: { value: "  " },
    })
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }))

    expect(
      await screen.findByText("First name is required"),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("saves the profile and confirms it", async () => {
    renderForm()

    fireEvent.change(screen.getByLabelText(/first name/i), {
      target: { value: "Huy Pham" },
    })
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain("/account/profile")
    expect(init?.method).toBe("PATCH")
    expect(JSON.parse(String(init?.body))).toEqual({
      firstName: "Huy Pham",
      lastName: "Pham",
      phone: "0900000000",
    })
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Profile updated",
    )
  })

  it("shows the backend message when the save fails", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse(
          {
            timestamp: "2026-10-22T00:00:00Z",
            status: 400,
            code: "VALIDATION_ERROR",
            message: "Request validation failed",
            fieldErrors: { firstName: "First name is too long" },
            path: "/api/v1/account/profile",
          },
          400,
        ),
      ),
    )
    renderForm()

    fireEvent.click(screen.getByRole("button", { name: /save changes/i }))

    expect(
      await screen.findByText("First name is too long"),
    ).toBeInTheDocument()
  })
})

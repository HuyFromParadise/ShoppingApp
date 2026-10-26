import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import { WishlistButton } from "./wishlist-button"

const push = vi.fn()
let sessionStatus: "loading" | "authenticated" | "unauthenticated" = "authenticated"

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
}))

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ status: sessionStatus, user: null }),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function stubServer(options: { saved: boolean; postStatus?: number }) {
  let saved = options.saved
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET"
    if (method === "GET") {
      return Promise.resolve(jsonResponse({ data: saved, message: "Success" }))
    }
    if (options.postStatus && options.postStatus >= 400) {
      return Promise.resolve(
        jsonResponse(
          { timestamp: "x", status: 409, code: "CONFLICT", message: "This product is no longer available" },
          options.postStatus,
        ),
      )
    }
    saved = method === "POST"
    return Promise.resolve(jsonResponse({ data: saved ? [{}] : [], message: "Success" }))
  })
  vi.stubGlobal("fetch", mock)
  return mock
}

function renderButton() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <WishlistButton productId="p1" productName="Oxford Shirt" />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("wishlist button", () => {
  beforeEach(() => {
    push.mockReset()
    sessionStatus = "authenticated"
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("shows the saved state from the server with a pressed toggle", async () => {
    stubServer({ saved: true })
    renderButton()

    const button = await screen.findByRole("button", { name: /Saved/ })
    await waitFor(() => expect(button).toHaveAttribute("aria-pressed", "true"))
  })

  it("saves an unsaved product and reports it", async () => {
    const mock = stubServer({ saved: false })
    renderButton()

    const button = await screen.findByRole("button", { name: /^Save/ })
    await waitFor(() => expect(button).not.toBeDisabled())
    fireEvent.click(button)

    await waitFor(() => expect(button).toHaveAttribute("aria-pressed", "true"))
    const post = mock.mock.calls.find(([, init]) => init?.method === "POST")
    expect(String(post?.[0])).toContain("/wishlist/items/p1")
    expect(await screen.findByText("Saved to your wishlist")).toBeInTheDocument()
  })

  it("removes an already saved product with DELETE", async () => {
    const mock = stubServer({ saved: true })
    renderButton()

    const button = await screen.findByRole("button", { name: /Saved/ })
    await waitFor(() => expect(button).not.toBeDisabled())
    fireEvent.click(button)

    await waitFor(() => expect(button).toHaveAttribute("aria-pressed", "false"))
    const call = mock.mock.calls.find(([, init]) => init?.method === "DELETE")
    expect(String(call?.[0])).toContain("/wishlist/items/p1")
  })

  it("sends a signed-out visitor to sign in instead of failing", async () => {
    const mock = stubServer({ saved: false })
    sessionStatus = "unauthenticated"
    renderButton()

    const button = await screen.findByRole("button", { name: /^Save/ })
    fireEvent.click(button)

    expect(push).toHaveBeenCalledWith(expect.stringContaining("/login?next="))
    // no write was attempted while signed out
    expect(mock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
  })

  it("surfaces a refusal from the server", async () => {
    stubServer({ saved: false, postStatus: 409 })
    renderButton()

    const button = await screen.findByRole("button", { name: /^Save/ })
    await waitFor(() => expect(button).not.toBeDisabled())
    fireEvent.click(button)

    expect(
      await screen.findByText("This product is no longer available"),
    ).toBeInTheDocument()
  })
})

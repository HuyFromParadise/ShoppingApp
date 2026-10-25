import { render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { RequireAdmin } from "./route-guard"

const replace = vi.fn()
let sessionState: { status: string; user: Record<string, unknown> | null } = {
  status: "loading",
  user: null,
}

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/admin/products",
}))

vi.mock("@/features/auth/session", () => ({
  useSession: () => sessionState,
}))

function renderGuard(children = <p>admin area</p>) {
  return render(<RequireAdmin>{children}</RequireAdmin>)
}

describe("admin route guard", () => {
  afterEach(() => {
    replace.mockReset()
  })

  it("shows a loading state and no admin content while the session loads", () => {
    sessionState = { status: "loading", user: null }
    renderGuard()
    expect(screen.queryByText("admin area")).not.toBeInTheDocument()
    expect(screen.getByText("Checking your session")).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it("redirects a signed-out visitor to sign in with a return path", () => {
    sessionState = { status: "unauthenticated", user: null }
    renderGuard()
    expect(replace).toHaveBeenCalledWith("/login?next=%2Fadmin%2Fproducts")
    expect(screen.queryByText("admin area")).not.toBeInTheDocument()
  })

  it("refuses a signed-in customer instead of showing an empty shell", () => {
    sessionState = {
      status: "authenticated",
      user: { id: "u1", role: "CUSTOMER" },
    }
    renderGuard()

    expect(screen.queryByText("admin area")).not.toBeInTheDocument()
    expect(
      screen.getByText("This area is for store administrators"),
    ).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it("renders the admin area for an administrator", () => {
    sessionState = { status: "authenticated", user: { id: "a1", role: "ADMIN" } }
    renderGuard()
    expect(screen.getByText("admin area")).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })
})

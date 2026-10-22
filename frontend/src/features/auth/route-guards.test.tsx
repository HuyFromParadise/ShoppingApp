import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { RequireAnonymous, RequireAuth } from "./route-guards"

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  push: vi.fn(),
  pathname: "/account/orders",
  status: "loading" as string,
}))

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
  usePathname: () => mocks.pathname,
}))

vi.mock("./session", () => ({
  useSession: () => ({ status: mocks.status, user: null }),
}))

describe("RequireAuth", () => {
  beforeEach(() => {
    mocks.status = "loading"
    mocks.replace.mockClear()
  })

  it("renders the protected content once the session is authenticated", () => {
    mocks.status = "authenticated"

    render(
      <RequireAuth>
        <p>Secret area</p>
      </RequireAuth>,
    )

    expect(screen.getByText("Secret area")).toBeInTheDocument()
    expect(mocks.replace).not.toHaveBeenCalled()
  })

  it("redirects to the login page when the session is anonymous", () => {
    mocks.status = "unauthenticated"

    render(
      <RequireAuth>
        <p>Secret area</p>
      </RequireAuth>,
    )

    expect(mocks.replace).toHaveBeenCalledWith(
      "/login?next=%2Faccount%2Forders",
    )
    expect(screen.queryByText("Secret area")).not.toBeInTheDocument()
  })

  it("keeps the content hidden while the session is still loading", () => {
    mocks.status = "loading"

    render(
      <RequireAuth>
        <p>Secret area</p>
      </RequireAuth>,
    )

    expect(screen.queryByText("Secret area")).not.toBeInTheDocument()
    expect(mocks.replace).not.toHaveBeenCalled()
  })
})

describe("RequireAnonymous", () => {
  beforeEach(() => {
    mocks.status = "loading"
    mocks.replace.mockClear()
  })

  it("renders the sign-in form for anonymous visitors", () => {
    mocks.status = "unauthenticated"

    render(
      <RequireAnonymous>
        <p>Sign in form</p>
      </RequireAnonymous>,
    )

    expect(screen.getByText("Sign in form")).toBeInTheDocument()
    expect(mocks.replace).not.toHaveBeenCalled()
  })

  it("sends an authenticated visitor away from the sign-in form", () => {
    mocks.status = "authenticated"

    render(
      <RequireAnonymous>
        <p>Sign in form</p>
      </RequireAnonymous>,
    )

    expect(mocks.replace).toHaveBeenCalledWith("/")
    expect(screen.queryByText("Sign in form")).not.toBeInTheDocument()
  })
})

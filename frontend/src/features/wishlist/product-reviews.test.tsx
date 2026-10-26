import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import type { ProductReviews as ProductReviewsData } from "@/types/p1"
import { ProductReviews, RatingStars } from "./product-reviews"

let sessionStatus: "loading" | "authenticated" | "unauthenticated" = "unauthenticated"

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ status: sessionStatus, user: null }),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function reviews(overrides: Partial<ProductReviewsData> = {}): ProductReviewsData {
  return {
    averageRating: 4.5,
    reviewCount: 2,
    ratingCounts: [0, 0, 0, 1, 1],
    items: [
      {
        id: "r1",
        rating: 5,
        title: "Perfect fit",
        comment: "Wears beautifully.",
        authorName: "An N.",
        verifiedPurchase: true,
        createdAt: "2026-10-20T00:00:00Z",
      },
      {
        id: "r2",
        rating: 4,
        title: null,
        comment: null,
        authorName: "Binh T.",
        verifiedPurchase: false,
        createdAt: "2026-10-19T00:00:00Z",
      },
    ],
    ...overrides,
  }
}

function stubServer(options: {
  reviews?: ProductReviewsData
  eligibility?: { signedIn: boolean; canReview: boolean; verifiedPurchase: boolean }
  postStatus?: number
}) {
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET"
    if (String(url).endsWith("/review-eligibility")) {
      return Promise.resolve(
        jsonResponse({
          data: options.eligibility ?? {
            signedIn: true,
            canReview: true,
            verifiedPurchase: false,
          },
          message: "Success",
        }),
      )
    }
    if (method === "POST") {
      if (options.postStatus === 409) {
        return Promise.resolve(
          jsonResponse(
            { timestamp: "x", status: 409, code: "CONFLICT", message: "You have already reviewed this product" },
            409,
          ),
        )
      }
      return Promise.resolve(jsonResponse({ data: { id: "r3" }, message: "Success" }, 201))
    }
    return Promise.resolve(jsonResponse({ data: options.reviews ?? reviews(), message: "Success" }))
  })
  vi.stubGlobal("fetch", mock)
  return mock
}

function renderSection() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ProductReviews slug="oxford-shirt" productName="Oxford Shirt" />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("product reviews", () => {
  beforeEach(() => {
    sessionStatus = "unauthenticated"
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("states the rating in text so it never relies on stars or colour", () => {
    render(<RatingStars rating={4} />)
    expect(screen.getByText("4 out of 5 stars")).toBeInTheDocument()
  })

  it("shows the summary and every review with its purchase badge", async () => {
    stubServer({})
    renderSection()

    expect(await screen.findByText(/4\.5 out of 5 from 2 reviews/)).toBeInTheDocument()
    expect(screen.getByText("Perfect fit")).toBeInTheDocument()
    expect(screen.getByText("Wears beautifully.")).toBeInTheDocument()
    expect(screen.getByText("Verified purchase")).toBeInTheDocument()
    expect(screen.getByText(/An N\./)).toBeInTheDocument()
    // the breakdown is described for screen readers, not only as a bar width
    expect(screen.getByText("1 of 2 reviews rated 5 stars")).toBeInTheDocument()
  })

  it("invites a signed-out visitor to sign in", async () => {
    stubServer({})
    renderSection()

    await screen.findByText(/4\.5 out of 5 from 2 reviews/)
    const link = screen.getByRole("link", { name: "Sign in" })
    expect(link).toHaveAttribute(
      "href",
      `/login?next=${encodeURIComponent("/products/oxford-shirt")}`,
    )
    expect(screen.queryByRole("button", { name: "Write a review" })).not.toBeInTheDocument()
  })

  it("offers the form to a customer who has not reviewed yet", async () => {
    sessionStatus = "authenticated"
    stubServer({})
    renderSection()

    fireEvent.click(await screen.findByRole("button", { name: "Write a review" }))
    fireEvent.click(screen.getByRole("radio", { name: "4 stars" }))
    fireEvent.change(screen.getByLabelText(/Title/), { target: { value: "Good" } })
    fireEvent.click(screen.getByRole("button", { name: "Publish review" }))

    expect(await screen.findByText("Thank you for your review")).toBeInTheDocument()
  })

  it("says so when the customer has already reviewed", async () => {
    sessionStatus = "authenticated"
    stubServer({
      eligibility: { signedIn: true, canReview: false, verifiedPurchase: false },
    })
    renderSection()

    expect(
      await screen.findByText("You have already reviewed Oxford Shirt. Thank you."),
    ).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Write a review" })).not.toBeInTheDocument()
  })

  it("shows a server refusal inside the form", async () => {
    sessionStatus = "authenticated"
    stubServer({ postStatus: 409 })
    renderSection()

    fireEvent.click(await screen.findByRole("button", { name: "Write a review" }))
    fireEvent.click(screen.getByRole("button", { name: "Publish review" }))

    expect(
      await screen.findByText("You have already reviewed this product"),
    ).toBeInTheDocument()
    // the form stays open so nothing typed is lost
    expect(screen.getByRole("button", { name: "Publish review" })).toBeInTheDocument()
  })

  it("invites the first review when there are none", async () => {
    stubServer({
      reviews: { averageRating: 0, reviewCount: 0, ratingCounts: [0, 0, 0, 0, 0], items: [] },
    })
    renderSection()

    expect(await screen.findByText("No reviews yet")).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByText("Be the first to review Oxford Shirt.")).toBeInTheDocument(),
    )
  })
})

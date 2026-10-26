import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { API_BASE_URL } from "@/lib/api/client"
import { tokenStore } from "@/lib/api/token"
import {
  createReview,
  getReviewEligibility,
  isInWishlist,
  listProductReviews,
  listWishlist,
  removeFromWishlist,
  saveToWishlist,
} from "./api"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

type FetchCall = [input: string, init?: RequestInit]

function stubFetch(...responses: Response[]) {
  const mock = vi.fn((_input: string, _init?: RequestInit) =>
    Promise.resolve(responses.shift() as Response),
  )
  vi.stubGlobal("fetch", mock)
  return mock as unknown as { mock: { calls: FetchCall[] } }
}

describe("wishlist and review api", () => {
  beforeEach(() => tokenStore.set("token"))
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it("lists the wishlist", async () => {
    const mock = stubFetch(jsonResponse({ data: [], message: "Success" }))
    await listWishlist()
    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/wishlist`)
  })

  it("saves with POST and removes with DELETE on the same product path", async () => {
    const mock = stubFetch(
      jsonResponse({ data: [{ productId: "p1" }], message: "Success" }),
      jsonResponse({ data: [], message: "Success" }),
    )

    const saved = await saveToWishlist("p1")
    await removeFromWishlist("p1")

    expect(saved).toHaveLength(1)
    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/wishlist/items/p1`)
    expect(mock.mock.calls[0][1]?.method).toBe("POST")
    expect(mock.mock.calls[1][0]).toBe(`${API_BASE_URL}/wishlist/items/p1`)
    expect(mock.mock.calls[1][1]?.method).toBe("DELETE")
  })

  it("reads the saved state and eligibility", async () => {
    const mock = stubFetch(
      jsonResponse({ data: true, message: "Success" }),
      jsonResponse({
        data: { signedIn: true, canReview: false, verifiedPurchase: true },
        message: "Success",
      }),
    )

    expect(await isInWishlist("p1")).toBe(true)
    const eligibility = await getReviewEligibility("oxford-shirt")
    expect(eligibility).toEqual({
      signedIn: true,
      canReview: false,
      verifiedPurchase: true,
    })
    expect(mock.mock.calls[1][0]).toBe(
      `${API_BASE_URL}/products/oxford-shirt/review-eligibility`,
    )
  })

  it("reads reviews for a slug and posts a new one", async () => {
    const mock = stubFetch(
      jsonResponse({
        data: { averageRating: 4.5, reviewCount: 2, ratingCounts: [0, 0, 0, 1, 1], items: [] },
        message: "Success",
      }),
      jsonResponse({ data: { id: "r1", rating: 5 }, message: "Success" }, 201),
    )

    const reviews = await listProductReviews("oxford-shirt")
    expect(reviews.reviewCount).toBe(2)
    await createReview("oxford-shirt", { rating: 5, comment: "Great" })

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/products/oxford-shirt/reviews`)
    expect(mock.mock.calls[1][1]?.method).toBe("POST")
    expect(JSON.parse(String(mock.mock.calls[1][1]?.body))).toEqual({
      rating: 5,
      comment: "Great",
    })
  })
})

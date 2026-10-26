import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type {
  CreateReviewInput,
  ProductReviews,
  Review,
  ReviewEligibility,
  WishlistEntry,
} from "@/types/p1"

/**
 * P1 endpoints: wishlist, product reviews and review eligibility.
 *
 * <p>Every call goes through the shared client, so the envelope, error codes and
 * the 401 → refresh → retry flow stay in one place (DESIGN_REFERENCE §9).
 */

export function listWishlist(): Promise<WishlistEntry[]> {
  return api.get<WishlistEntry[]>(ENDPOINTS.wishlist)
}

/** Saving is idempotent; the response is the whole wishlist either way. */
export function saveToWishlist(productId: string): Promise<WishlistEntry[]> {
  return api.post<WishlistEntry[]>(ENDPOINTS.wishlistItem(productId))
}

export function removeFromWishlist(productId: string): Promise<WishlistEntry[]> {
  return api.delete<WishlistEntry[]>(ENDPOINTS.wishlistItem(productId))
}

export function isInWishlist(productId: string): Promise<boolean> {
  return api.get<boolean>(ENDPOINTS.wishlistItem(productId))
}

/** Public: reviews of an active product, newest first. */
export function listProductReviews(slug: string): Promise<ProductReviews> {
  return api.get<ProductReviews>(ENDPOINTS.productReviews(slug))
}

export function getReviewEligibility(slug: string): Promise<ReviewEligibility> {
  return api.get<ReviewEligibility>(ENDPOINTS.productReviewEligibility(slug))
}

export function createReview(
  slug: string,
  input: CreateReviewInput,
): Promise<Review> {
  return api.post<Review>(ENDPOINTS.productReviews(slug), input)
}

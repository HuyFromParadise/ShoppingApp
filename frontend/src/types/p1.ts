/** P1 DTOs: wishlist, promotions and reviews (step 17). */

export interface WishlistEntry {
  productId: string
  productName: string
  productSlug: string
  currency: string
  priceMinor: number
  compareAtPriceMinor: number | null
  inStock: boolean
  /** False once the product is archived: it can be removed but not bought. */
  purchasable: boolean
  imageUrl: string | null
  savedAt: string | null
}

export interface Review {
  id: string
  rating: number
  title: string | null
  comment: string | null
  /** First name plus an initial; never an email or id. */
  authorName: string
  verifiedPurchase: boolean
  createdAt: string | null
}

export interface ProductReviews {
  averageRating: number
  reviewCount: number
  /** Index 0 = 1 star … index 4 = 5 stars. */
  ratingCounts: number[]
  items: Review[]
}

export interface ReviewEligibility {
  signedIn: boolean
  canReview: boolean
  verifiedPurchase: boolean
}

export interface CreateReviewInput {
  rating: number
  title?: string
  comment?: string
}

export type PromotionType = "PERCENT" | "FIXED"

export interface AdminPromotion {
  id: string
  code: string
  name: string | null
  description: string | null
  type: PromotionType
  value: number
  minOrderMinor: number
  maxDiscountMinor: number | null
  startsAt: string | null
  endsAt: string | null
  active: boolean
}

export interface CreatePromotionInput {
  code: string
  name?: string
  description?: string
  type: PromotionType
  value: number
  minOrder?: number
  maxDiscount?: number
  startsAt?: string
  endsAt?: string
  active?: boolean
}

export interface UpdatePromotionInput {
  name?: string
  description?: string
  type?: PromotionType
  value?: number
  minOrder?: number
  maxDiscount?: number
  startsAt?: string
  endsAt?: string
  active?: boolean
}

/** "10% off" or "50,000 ₫ off", from the stored type and value. */
export function describePromotion(promotion: AdminPromotion): string {
  return promotion.type === "PERCENT"
    ? `${promotion.value}% off`
    : `${promotion.value.toLocaleString("vi-VN")} off`
}

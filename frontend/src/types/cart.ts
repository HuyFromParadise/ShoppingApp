/** Shared cart DTOs mirroring the backend cart responses. */

/** Per-line cap enforced by the backend (`quantity` 1–10, and ≤ variant stock). */
export const MAX_LINE_QUANTITY = 10

export interface CartItem {
  id: string
  variantId: string
  productId: string
  productName: string
  productSlug: string
  imageUrl: string | null
  sku: string
  size: string | null
  color: string | null
  unitPriceMinor: number
  quantity: number
  lineTotalMinor: number
  stockQuantity: number
  /** Server truth: product active, variant active and stock covering the line. */
  available: boolean
}

export interface Cart {
  id: string
  items: CartItem[]
  itemCount: number
  subtotalMinor: number
  currency: string
}

export interface AddCartItemInput {
  variantId: string
  quantity: number
}

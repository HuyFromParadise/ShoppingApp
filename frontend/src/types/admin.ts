/** Admin DTOs mirroring the backend admin responses (step 15). */

export type ProductStatus = "DRAFT" | "ACTIVE" | "ARCHIVED"

export const PRODUCT_STATUSES: readonly ProductStatus[] = [
  "DRAFT",
  "ACTIVE",
  "ARCHIVED",
]

export function productStatusLabel(status: string): string {
  switch (status) {
    case "DRAFT":
      return "Draft"
    case "ACTIVE":
      return "Live"
    case "ARCHIVED":
      return "Archived"
    default:
      return status
  }
}

export interface AdminProductSummary {
  id: string
  name: string
  slug: string
  categoryId: string
  categoryName: string | null
  basePriceMinor: number
  currency: string
  status: string
  featured: boolean
  variantCount: number
  totalStock: number
  primaryImageUrl: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminImage {
  id: string
  url: string
  altText: string | null
  sortOrder: number
  primary: boolean
}

export interface AdminVariant {
  id: string
  sku: string
  size: string | null
  color: string | null
  priceOverrideMinor: number | null
  effectivePriceMinor: number
  stockQuantity: number
  active: boolean
}

export interface AdminProductDetail {
  id: string
  name: string
  slug: string
  shortDescription: string | null
  description: string | null
  categoryId: string
  categoryName: string | null
  basePriceMinor: number
  currency: string
  status: string
  material: string | null
  fit: string | null
  careInstructions: string | null
  featured: boolean
  images: AdminImage[]
  variants: AdminVariant[]
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminVariantInput {
  sku?: string
  size?: string
  color?: string
  priceOverride?: number
  stockQuantity: number
  active?: boolean
}

export interface CreateProductInput {
  name: string
  slug?: string
  shortDescription?: string
  description?: string
  categoryId: string
  basePrice: number
  currency?: string
  status?: ProductStatus
  material?: string
  fit?: string
  careInstructions?: string
  featured?: boolean
  variants: AdminVariantInput[]
}

/** Partial edit; absent fields stay unchanged on the server. */
export interface UpdateProductInput {
  name?: string
  shortDescription?: string
  description?: string
  categoryId?: string
  basePrice?: number
  status?: ProductStatus
  material?: string
  fit?: string
  careInstructions?: string
  featured?: boolean
}

export interface UpdateVariantInput {
  sku?: string
  size?: string
  color?: string
  priceOverride?: number
  active?: boolean
}

export interface AdminCategory {
  id: string
  name: string
  slug: string
  description: string | null
  parentId: string | null
  active: boolean
  sortOrder: number
  productCount: number
}

export interface CreateCategoryInput {
  name: string
  slug?: string
  description?: string
  parentId?: string
  active?: boolean
  sortOrder?: number
}

export interface UpdateCategoryInput {
  name?: string
  description?: string
  parentId?: string
  active?: boolean
  sortOrder?: number
}

export interface AdminOrderSummary {
  id: string
  orderNumber: string
  status: string
  paymentStatus: string
  currency: string
  grandTotalMinor: number
  itemCount: number
  customerId: string
  customerEmail: string | null
  shipRecipientName: string | null
  placedAt: string | null
}

export interface RevenueWindow {
  since: string | null
  paidOrders: number
  revenueMinor: number
  averageOrderValueMinor: number
  ordersPlaced: number
}

export interface LowStockVariant {
  variantId: string
  sku: string
  size: string | null
  color: string | null
  stockQuantity: number
  productName: string
  productId: string
}

export interface AdminDashboardSummary {
  ordersByStatus: Record<string, number>
  revenueLast30Days: RevenueWindow
  revenueAllTime: RevenueWindow
  activeProducts: number
  draftProducts: number
  archivedProducts: number
  lowStock: LowStockVariant[]
  lowStockThreshold: number
  generatedAt: string | null
}

/**
 * Fulfillment transitions an admin may request, mirroring the backend
 * `OrderStateMachine` (DESIGN_REFERENCE §7). The server still validates: this
 * list only decides which buttons are offered.
 *
 * `RETURN_REQUESTED` is absent on purpose: it is the customer's transition, and
 * there is no admin endpoint to file one on their behalf.
 */
export const ADMIN_ORDER_TARGETS: Record<string, readonly string[]> = {
  PENDING_PAYMENT: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED"],
  DELIVERED: [],
  RETURN_REQUESTED: ["RETURNED", "SHIPPED", "DELIVERED"],
  CANCELLED: [],
  RETURNED: [],
}

/** Actions the admin sees for a status, with a label and a destructive flag. */
export interface AdminOrderAction {
  target: string
  label: string
  destructive: boolean
  /** Worth warning about: it moves money (a refund) or frees stock. */
  confirm: boolean
}

export function adminOrderActions(status: string): AdminOrderAction[] {
  switch (status) {
    case "PENDING_PAYMENT":
    case "CONFIRMED":
      return [
        { target: status === "PENDING_PAYMENT" ? "CONFIRMED" : "PROCESSING",
          label: status === "PENDING_PAYMENT" ? "Mark paid" : "Start processing",
          destructive: false, confirm: false },
        { target: "CANCELLED", label: "Cancel order", destructive: true, confirm: true },
      ]
    case "PROCESSING":
      return [
        { target: "SHIPPED", label: "Mark shipped", destructive: false, confirm: false },
        { target: "CANCELLED", label: "Cancel order", destructive: true, confirm: true },
      ]
    case "RETURN_REQUESTED":
      return [
        { target: "RETURNED", label: "Accept return", destructive: false, confirm: true },
        { target: "SHIPPED", label: "Reject return", destructive: false, confirm: false },
        // Filed by mistake: re-open the delivered state without restocking.
        { target: "DELIVERED", label: "Reject and keep delivered", destructive: false, confirm: false },
      ]
    case "SHIPPED":
      return [
        { target: "DELIVERED", label: "Mark delivered", destructive: false, confirm: false },
      ]
    default:
      return []
  }
}

export const UPDATE_ORDER_STATUS_NOTE_MAX = 500

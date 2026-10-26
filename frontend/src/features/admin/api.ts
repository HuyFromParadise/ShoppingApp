import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type {
  AdminCategory,
  AdminDashboardSummary,
  AdminOrderSummary,
  AdminProductDetail,
  AdminProductSummary,
  AdminVariant,
  CreateCategoryInput,
  CreateProductInput,
  AdminVariantInput,
  UpdateCategoryInput,
  UpdateProductInput,
  UpdateVariantInput,
} from "@/types/admin"
import type { OrderDetail } from "@/types/orders"

/**
 * Admin endpoints (DESIGN_REFERENCE §5.3, Phase 6). Everything here is
 * ADMIN-only on the server; the route guard is only UX.
 *
 * <p>Note what is absent: the backend exposes no admin endpoint for a return
 * request, so the UI does not offer one.
 */

export function getDashboardSummary(): Promise<AdminDashboardSummary> {
  return api.get<AdminDashboardSummary>(ENDPOINTS.admin.dashboardSummary)
}

export interface AdminProductFilters {
  q?: string
  status?: string
  categoryId?: string
  page: number
  size: number
}

export function listProducts(filters: AdminProductFilters) {
  return api.getPage<AdminProductSummary>(ENDPOINTS.admin.products, {
    query: {
      q: filters.q,
      status: filters.status,
      categoryId: filters.categoryId,
      page: filters.page,
      size: filters.size,
    },
  })
}

export function getProduct(productId: string): Promise<AdminProductDetail> {
  return api.get<AdminProductDetail>(ENDPOINTS.admin.product(productId))
}

export function createProduct(input: CreateProductInput): Promise<AdminProductDetail> {
  return api.post<AdminProductDetail>(ENDPOINTS.admin.products, input)
}

/**
 * Creates a product and its first image in one multipart request.
 *
 * <p>Images can only be uploaded against an existing product id, so without
 * this the new-product page could not attach a photograph at all. The product
 * fields travel as a JSON `product` part and the file as `file`; the server
 * validates the image before it writes anything, so a rejected file creates no
 * product.
 */
export function createProductWithImage(
  input: CreateProductInput,
  file: File,
  altText?: string,
): Promise<AdminProductDetail> {
  const body = new FormData()
  // A Blob part is what makes Spring treat this as a part rather than a field.
  body.append(
    "product",
    new Blob([JSON.stringify(input)], { type: "application/json" }),
  )
  body.append("file", file)
  if (altText?.trim()) body.append("altText", altText.trim())
  return api.post<AdminProductDetail>(ENDPOINTS.admin.products, body)
}

export function updateProduct(
  productId: string,
  input: UpdateProductInput,
): Promise<AdminProductDetail> {
  return api.patch<AdminProductDetail>(ENDPOINTS.admin.product(productId), input)
}

export function addVariant(
  productId: string,
  input: AdminVariantInput,
): Promise<AdminProductDetail> {
  return api.post<AdminProductDetail>(
    ENDPOINTS.admin.productVariants(productId),
    input,
  )
}

export function updateVariant(
  variantId: string,
  input: UpdateVariantInput,
): Promise<AdminVariant> {
  return api.patch<AdminVariant>(ENDPOINTS.admin.variant(variantId), input)
}

/** Absolute stock set; the reason is stored in the audit trail. */
export function setVariantStock(
  variantId: string,
  stockQuantity: number,
  reason?: string,
): Promise<AdminVariant> {
  return api.patch<AdminVariant>(ENDPOINTS.admin.variantStock(variantId), {
    stockQuantity,
    reason: reason?.trim() || undefined,
  })
}

export function uploadProductImage(
  productId: string,
  file: File,
  altText?: string,
): Promise<unknown> {
  const body = new FormData()
  body.append("file", file)
  if (altText?.trim()) body.append("altText", altText.trim())
  return api.post(ENDPOINTS.admin.productImages(productId), body)
}

export function deleteProductImage(productId: string, imageId: string): Promise<void> {
  return api.delete(ENDPOINTS.admin.productImage(productId, imageId))
}

export function listCategories(): Promise<AdminCategory[]> {
  return api.get<AdminCategory[]>(ENDPOINTS.admin.categories)
}

export function createCategory(input: CreateCategoryInput): Promise<AdminCategory> {
  return api.post<AdminCategory>(ENDPOINTS.admin.categories, input)
}

export function updateCategory(
  categoryId: string,
  input: UpdateCategoryInput,
): Promise<AdminCategory> {
  return api.patch<AdminCategory>(ENDPOINTS.admin.category(categoryId), input)
}

export interface AdminOrderFilters {
  status?: string
  q?: string
  page: number
  size: number
}

export function listOrders(filters: AdminOrderFilters) {
  return api.getPage<AdminOrderSummary>(ENDPOINTS.admin.orders, {
    query: {
      status: filters.status,
      q: filters.q,
      page: filters.page,
      size: filters.size,
    },
  })
}

export function getOrder(orderId: string): Promise<OrderDetail> {
  return api.get<OrderDetail>(ENDPOINTS.admin.order(orderId))
}

export function changeOrderStatus(
  orderId: string,
  status: string,
  note?: string,
): Promise<OrderDetail> {
  return api.patch<OrderDetail>(ENDPOINTS.admin.orderStatus(orderId), {
    status,
    note: note?.trim() || undefined,
  })
}

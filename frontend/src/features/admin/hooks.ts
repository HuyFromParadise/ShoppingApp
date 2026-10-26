"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  addVariant,
  changeOrderStatus,
  createCategory,
  createProduct,
  createProductWithImage,
  deleteProductImage,
  getDashboardSummary,
  getOrder,
  getProduct,
  listCategories,
  listOrders,
  listProducts,
  setVariantStock,
  updateCategory,
  updateProduct,
  updateVariant,
  uploadProductImage,
  type AdminOrderFilters,
  type AdminProductFilters,
} from "./api"
import {
  createPromotion,
  listPromotions,
  updatePromotion,
} from "./promotions-api"
import type {
  AdminVariantInput,
  CreateCategoryInput,
  CreateProductInput,
  UpdateCategoryInput,
  UpdateProductInput,
  UpdateVariantInput,
} from "@/types/admin"
import type { CreatePromotionInput, UpdatePromotionInput } from "@/types/p1"

/**
 * Admin queries and mutations. Every mutation re-reads the affected list or
 * detail instead of patching a local copy: status, stock and audit data all
 * have server rules, and a guessed local state would hide a rejection.
 */
export const ADMIN_KEYS = {
  dashboard: ["admin", "dashboard"] as const,
  promotions: ["admin", "promotions"] as const,
  products: (filters: AdminProductFilters) =>
    [
      "admin",
      "products",
      filters.q ?? "",
      filters.status ?? "",
      filters.categoryId ?? "",
      filters.page,
      filters.size,
    ] as const,
  product: (productId: string) => ["admin", "product", productId] as const,
  categories: ["admin", "categories"] as const,
  orders: (filters: AdminOrderFilters) =>
    [
      "admin",
      "orders",
      filters.status ?? "",
      filters.q ?? "",
      filters.page,
      filters.size,
    ] as const,
  order: (orderId: string) => ["admin", "order", orderId] as const,
}

export function useDashboard() {
  return useQuery({
    queryKey: ADMIN_KEYS.dashboard,
    queryFn: getDashboardSummary,
  })
}

export function useAdminProducts(filters: AdminProductFilters) {
  return useQuery({
    queryKey: ADMIN_KEYS.products(filters),
    queryFn: () => listProducts(filters),
  })
}

export function useAdminProduct(productId: string | undefined) {
  return useQuery({
    queryKey: ADMIN_KEYS.product(productId ?? ""),
    queryFn: () => getProduct(productId as string),
    enabled: Boolean(productId),
  })
}

export function useAdminCategories() {
  return useQuery({
    queryKey: ADMIN_KEYS.categories,
    queryFn: listCategories,
  })
}

export function useAdminOrders(filters: AdminOrderFilters) {
  return useQuery({
    queryKey: ADMIN_KEYS.orders(filters),
    queryFn: () => listOrders(filters),
  })
}

export function useAdminOrder(orderId: string | undefined) {
  return useQuery({
    queryKey: ADMIN_KEYS.order(orderId ?? ""),
    queryFn: () => getOrder(orderId as string),
    enabled: Boolean(orderId),
  })
}

/** Anything that can change a product also refreshes the product lists. */
function useInvalidateProducts() {
  const queryClient = useQueryClient()
  return (productId?: string) => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "products"] })
    void queryClient.invalidateQueries({ queryKey: ["admin", "categories"] })
    if (productId) {
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.product(productId) })
    }
  }
}

export function useCreateProduct() {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: ({
      input,
      image,
      altText,
    }: {
      input: CreateProductInput
      /** Optional first photograph, sent in the same request as the product. */
      image?: File | null
      altText?: string
    }) =>
      image
        ? createProductWithImage(input, image, altText)
        : createProduct(input),
    onSuccess: (product) => invalidate(product.id),
  })
}

export function useUpdateProduct(productId: string) {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: (input: UpdateProductInput) => updateProduct(productId, input),
    onSuccess: () => invalidate(productId),
  })
}

export function useAddVariant(productId: string) {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: (input: AdminVariantInput) => addVariant(productId, input),
    onSuccess: () => invalidate(productId),
  })
}

export function useUpdateVariant(productId: string) {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: ({ variantId, input }: { variantId: string; input: UpdateVariantInput }) =>
      updateVariant(variantId, input),
    onSuccess: () => invalidate(productId),
  })
}

/** Absolute stock set. Also refreshes the dashboard, which reports low stock. */
export function useSetStock(productId?: string) {
  const invalidate = useInvalidateProducts()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      variantId,
      stockQuantity,
      reason,
    }: {
      variantId: string
      stockQuantity: number
      reason?: string
    }) => setVariantStock(variantId, stockQuantity, reason),
    onSuccess: () => {
      invalidate(productId)
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.dashboard })
    },
  })
}

export function useUploadImage(productId: string) {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: ({ file, altText }: { file: File; altText?: string }) =>
      uploadProductImage(productId, file, altText),
    onSuccess: () => invalidate(productId),
  })
}

export function useDeleteImage(productId: string) {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: (imageId: string) => deleteProductImage(productId, imageId),
    onSuccess: () => invalidate(productId),
  })
}

export function useCreateCategory() {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: (input: CreateCategoryInput) => createCategory(input),
    onSuccess: () => invalidate(),
  })
}

export function useUpdateCategory() {
  const invalidate = useInvalidateProducts()
  return useMutation({
    mutationFn: ({ categoryId, input }: { categoryId: string; input: UpdateCategoryInput }) =>
      updateCategory(categoryId, input),
    onSuccess: () => invalidate(),
  })
}

export function useChangeOrderStatus(orderId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ status, note }: { status: string; note?: string }) =>
      changeOrderStatus(orderId, status, note),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.order(orderId) })
      void queryClient.invalidateQueries({ queryKey: ["admin", "orders"] })
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.dashboard })
    },
  })
}


export function usePromotions(enabled = true) {
  return useQuery({
    queryKey: ADMIN_KEYS.promotions,
    queryFn: listPromotions,
    enabled,
  })
}

export function useCreatePromotion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreatePromotionInput) => createPromotion(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.promotions })
    },
  })
}

export function useUpdatePromotion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      promotionId,
      input,
    }: {
      promotionId: string
      input: UpdatePromotionInput
    }) => updatePromotion(promotionId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.promotions })
    },
  })
}

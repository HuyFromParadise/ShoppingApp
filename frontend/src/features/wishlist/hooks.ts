"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  createReview,
  getReviewEligibility,
  isInWishlist,
  listProductReviews,
  listWishlist,
  removeFromWishlist,
  saveToWishlist,
} from "./api"
import type { CreateReviewInput } from "@/types/p1"

export const WISHLIST_KEYS = {
  all: ["wishlist"] as const,
  contains: (productId: string) => ["wishlist", "contains", productId] as const,
  reviews: (slug: string) => ["reviews", slug] as const,
  eligibility: (slug: string) => ["reviews", slug, "eligibility"] as const,
}

export function useWishlist(enabled = true) {
  return useQuery({
    queryKey: WISHLIST_KEYS.all,
    queryFn: listWishlist,
    enabled,
  })
}

/** Toggle state for a product page; only asked for a signed-in customer. */
export function useIsWishlisted(productId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: WISHLIST_KEYS.contains(productId ?? ""),
    queryFn: () => isInWishlist(productId as string),
    enabled: enabled && Boolean(productId),
  })
}

/**
 * Save/remove. The wishlist is replaced with the server's answer rather than
 * toggled locally, because saving is idempotent and the response is the truth.
 */
export function useToggleWishlist() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      productId,
      saved,
    }: {
      productId: string
      saved: boolean
    }) =>
      saved ? removeFromWishlist(productId) : saveToWishlist(productId),
    onSuccess: (entries, { productId, saved }) => {
      queryClient.setQueryData(WISHLIST_KEYS.all, entries)
      queryClient.setQueryData(WISHLIST_KEYS.contains(productId), !saved)
    },
  })
}

export function useProductReviews(slug: string | undefined) {
  return useQuery({
    queryKey: WISHLIST_KEYS.reviews(slug ?? ""),
    queryFn: () => listProductReviews(slug as string),
    enabled: Boolean(slug),
  })
}

export function useReviewEligibility(slug: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: WISHLIST_KEYS.eligibility(slug ?? ""),
    queryFn: () => getReviewEligibility(slug as string),
    enabled: enabled && Boolean(slug),
  })
}

export function useCreateReview(slug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateReviewInput) => createReview(slug, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: WISHLIST_KEYS.reviews(slug) })
      void queryClient.invalidateQueries({ queryKey: WISHLIST_KEYS.eligibility(slug) })
    },
  })
}

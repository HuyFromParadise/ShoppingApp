"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { addCartItem, clearCart, getCart, removeCartItem, updateCartItem } from "./api"
import type { AddCartItemInput } from "@/types/cart"

/**
 * Cart queries and mutations. Every change invalidates the single cart key, so
 * the page re-reads the server's totals instead of trusting a local copy.
 */

export const CART_KEYS = {
  cart: ["cart"] as const,
}

export function useCart() {
  return useQuery({
    queryKey: CART_KEYS.cart,
    queryFn: getCart,
  })
}

export function useAddCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AddCartItemInput) => addCartItem(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CART_KEYS.cart }),
  })
}

export function useUpdateCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      itemId,
      quantity,
    }: {
      itemId: string
      quantity: number
    }) => updateCartItem(itemId, quantity),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CART_KEYS.cart }),
  })
}

export function useRemoveCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (itemId: string) => removeCartItem(itemId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CART_KEYS.cart }),
  })
}

export function useClearCart() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => clearCart(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CART_KEYS.cart }),
  })
}

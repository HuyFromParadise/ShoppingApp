import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type { AddCartItemInput, Cart } from "@/types/cart"

/**
 * Cart endpoints. Authenticated: the API client attaches the in-memory access
 * token and refreshes it when it expired. Add and PATCH return the whole cart.
 */

export function getCart(): Promise<Cart> {
  return api.get<Cart>(ENDPOINTS.cart)
}

export function addCartItem(input: AddCartItemInput): Promise<Cart> {
  return api.post<Cart>(ENDPOINTS.cartItems, input)
}

export function updateCartItem(
  itemId: string,
  quantity: number,
): Promise<Cart> {
  return api.patch<Cart>(ENDPOINTS.cartItem(itemId), { quantity })
}

export function removeCartItem(itemId: string): Promise<void> {
  return api.delete<void>(ENDPOINTS.cartItem(itemId))
}

export function clearCart(): Promise<void> {
  return api.delete<void>(ENDPOINTS.cart)
}

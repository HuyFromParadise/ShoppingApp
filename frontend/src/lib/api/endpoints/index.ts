/** Backend paths relative to `NEXT_PUBLIC_API_BASE_URL` (which ends in `/api/v1`). */
export const ENDPOINTS = {
  products: "/products",
  product: (slug: string) => `/products/${encodeURIComponent(slug)}`,
  categories: "/categories",
  categoryProducts: (slug: string) =>
    `/categories/${encodeURIComponent(slug)}/products`,
  auth: {
    register: "/auth/register",
    login: "/auth/login",
    refresh: "/auth/refresh",
    logout: "/auth/logout",
    me: "/auth/me",
  },
  account: {
    profile: "/account/profile",
    addresses: "/account/addresses",
    address: (addressId: string) =>
      `/account/addresses/${encodeURIComponent(addressId)}`,
  },
  cart: "/cart",
  cartItems: "/cart/items",
  cartItem: (itemId: string) => `/cart/items/${encodeURIComponent(itemId)}`,
  orders: "/orders",
  order: (orderId: string) => `/orders/${encodeURIComponent(orderId)}`,
  orderCancel: (orderId: string) =>
    `/orders/${encodeURIComponent(orderId)}/cancel`,
} as const

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
  orderReturn: (orderId: string) =>
    `/orders/${encodeURIComponent(orderId)}/return`,
  checkout: {
    quote: "/checkout/quote",
  },
  // The mock flow is the only enabled provider until VNPay is configured (ADR-007).
  payments: {
    order: (orderId: string) =>
      `/payments/${encodeURIComponent(orderId)}`,
    vnpayCreate: (orderId: string) =>
      `/payments/${encodeURIComponent(orderId)}/vnpay/create`,
    mockCreate: (orderId: string) =>
      `/payments/${encodeURIComponent(orderId)}/mock/create`,
    mockConfirm: (paymentId: string) =>
      `/payments/mock/${encodeURIComponent(paymentId)}/confirm`,
  },
  // P1: wishlist, reviews and promotions (step 17).
  wishlist: "/wishlist",
  wishlistItem: (productId: string) =>
    `/wishlist/items/${encodeURIComponent(productId)}`,
  productReviews: (slug: string) =>
    `/products/${encodeURIComponent(slug)}/reviews`,
  productReviewEligibility: (slug: string) =>
    `/products/${encodeURIComponent(slug)}/review-eligibility`,
  // Admin surface (DESIGN_REFERENCE §5.3, Phase 6). ADMIN-only server-side.
  admin: {
    products: "/admin/products",
    product: (productId: string) =>
      `/admin/products/${encodeURIComponent(productId)}`,
    productVariants: (productId: string) =>
      `/admin/products/${encodeURIComponent(productId)}/variants`,
    productImages: (productId: string) =>
      `/admin/products/${encodeURIComponent(productId)}/images`,
    productImage: (productId: string, imageId: string) =>
      `/admin/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`,
    variant: (variantId: string) =>
      `/admin/variants/${encodeURIComponent(variantId)}`,
    variantStock: (variantId: string) =>
      `/admin/variants/${encodeURIComponent(variantId)}/stock`,
    categories: "/admin/categories",
    category: (categoryId: string) =>
      `/admin/categories/${encodeURIComponent(categoryId)}`,
    orders: "/admin/orders",
    order: (orderId: string) =>
      `/admin/orders/${encodeURIComponent(orderId)}`,
    orderStatus: (orderId: string) =>
      `/admin/orders/${encodeURIComponent(orderId)}/status`,
    dashboardSummary: "/admin/dashboard/summary",
    promotions: "/admin/promotions",
    promotion: (promotionId: string) =>
      `/admin/promotions/${encodeURIComponent(promotionId)}`,
  },
} as const

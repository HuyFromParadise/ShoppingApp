export interface NavItem {
  href: string
  label: string
}

export const primaryNav: NavItem[] = [
  { href: "/shop", label: "Shop" },
  { href: "/cart", label: "Cart" },
  { href: "/wishlist", label: "Wishlist" },
  { href: "/account/orders", label: "Orders" },
  { href: "/account", label: "Account" },
]

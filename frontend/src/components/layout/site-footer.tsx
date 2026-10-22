import Link from "next/link"

const footerLinks = [
  { href: "/shop", label: "Shop" },
  { href: "/wishlist", label: "Wishlist" },
  { href: "/account", label: "Account" },
  { href: "/cart", label: "Cart" },
]

export function SiteFooter() {
  return (
    <footer className="mt-20 border-t border-line bg-paper">
      <div className="container-store grid gap-10 py-12 sm:grid-cols-2 lg:grid-cols-4">
        <div className="sm:col-span-2">
          <p className="text-sm font-semibold tracking-[0.3em] text-ink">
            CLOTHING
          </p>
          <p className="mt-3 max-w-sm text-sm text-muted">
            Considered clothing for everyday life. Built for a calm, uncluttered
            shopping experience.
          </p>
        </div>

        <nav aria-label="Footer">
          <h2 className="text-xs font-medium uppercase tracking-label text-muted">
            Browse
          </h2>
          <ul className="mt-4 space-y-2">
            {footerLinks.map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="text-sm text-ink transition-colors duration-150 hover:text-accent"
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div>
          <h2 className="text-xs font-medium uppercase tracking-label text-muted">
            Store
          </h2>
          <p className="mt-4 text-sm text-muted">
            Secure checkout in Vietnamese đồng. Prices include applicable taxes.
          </p>
        </div>
      </div>

      <div className="border-t border-line">
        <div className="container-store flex flex-col gap-2 py-6 text-xs text-muted sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} Clothing. All rights reserved.</p>
          <p>Made for a considered wardrobe.</p>
        </div>
      </div>
    </footer>
  )
}

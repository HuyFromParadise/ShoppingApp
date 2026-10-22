import Link from "next/link"
import { MobileNavigation } from "./mobile-navigation"
import { primaryNav } from "./nav-items"

export function SiteHeader() {
  return (
    <header className="relative border-b border-line bg-paper">
      <div className="container-store flex h-16 items-center justify-between gap-4">
        <Link
          href="/"
          className="text-base font-semibold tracking-[0.3em] text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
          aria-label="Clothing — home"
        >
          CLOTHING
        </Link>

        <nav aria-label="Primary" className="hidden md:block">
          <ul className="flex items-center gap-8">
            {primaryNav.map((item) => (
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

        <div className="flex items-center gap-5">
          <Link
            href="/cart"
            className="hidden text-sm text-ink transition-colors duration-150 hover:text-accent sm:inline"
          >
            Cart
          </Link>
          <MobileNavigation />
        </div>
      </div>
    </header>
  )
}

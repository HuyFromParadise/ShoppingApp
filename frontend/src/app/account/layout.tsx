import Link from "next/link"
import { SiteFooter } from "@/components/layout/site-footer"
import { SiteHeader } from "@/components/layout/site-header"
import { RequireAuth } from "@/features/auth/route-guards"

const accountNav = [
  { href: "/account/profile", label: "Profile" },
  { href: "/account/addresses", label: "Addresses" },
]

/**
 * Account shell. The guard only shapes the UX: every account endpoint is
 * authorized by the backend.
 */
export default function AccountLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:border focus:border-ink focus:bg-surface focus:px-4 focus:py-2 focus:text-sm focus:text-ink"
      >
        Skip to content
      </a>
      <SiteHeader />
      <main id="main-content" className="flex-1">
        <RequireAuth>
          <div className="container-store py-10 sm:py-14">
            <h1 className="text-2xl font-semibold tracking-tight text-ink">
              My account
            </h1>
            <nav aria-label="Account" className="mt-6 border-b border-line">
              <ul className="flex gap-6">
                {accountNav.map((item) => (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      className="inline-block border-b-2 border-transparent pb-3 text-sm text-muted transition-colors duration-150 hover:border-ink hover:text-ink"
                    >
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
            <div className="py-8">{children}</div>
          </div>
        </RequireAuth>
      </main>
      <SiteFooter />
    </div>
  )
}

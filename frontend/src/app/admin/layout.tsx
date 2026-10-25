"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { SiteFooter } from "@/components/layout/site-footer"
import { SiteHeader } from "@/components/layout/site-header"
import { RequireAdmin } from "@/features/admin/route-guard"
import { cn } from "@/lib/utils/cn"

const adminNav = [
  { href: "/admin", label: "Dashboard" },
  { href: "/admin/products", label: "Products" },
  { href: "/admin/categories", label: "Categories" },
  { href: "/admin/inventory", label: "Inventory" },
  { href: "/admin/orders", label: "Orders" },
]

/**
 * Admin shell (PROJECT_SPEC §6). Rendered on the storefront's shell so an
 * operator keeps the site header and a way back to the shop; the guard is UX
 * only and the API is the real boundary.
 */
export default function AdminLayout({
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
        <RequireAdmin>
          <div className="container-store py-10 sm:py-12">
            <p className="text-xs font-medium uppercase tracking-label text-muted">
              Store administration
            </p>
            <AdminNav />
            <div className="py-8">{children}</div>
          </div>
        </RequireAdmin>
      </main>
      <SiteFooter />
    </div>
  )
}

function AdminNav() {
  const pathname = usePathname()
  return (
    <nav aria-label="Administration" className="mt-3 border-b border-line">
      <ul className="flex flex-wrap gap-x-6 gap-y-1">
        {adminNav.map((item) => {
          // "/admin" must not stay active on every child route.
          const active =
            item.href === "/admin"
              ? pathname === "/admin"
              : pathname === item.href || pathname.startsWith(`${item.href}/`)
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-block border-b-2 pb-3 text-sm transition-colors duration-150",
                  active
                    ? "border-ink text-ink"
                    : "border-transparent text-muted hover:border-ink hover:text-ink",
                )}
              >
                {item.label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import * as React from "react"
import { primaryNav } from "./nav-items"

export function MobileNavigation() {
  const [open, setOpen] = React.useState(false)
  const pathname = usePathname()

  React.useEffect(() => {
    setOpen(false)
  }, [pathname])

  return (
    <div className="md:hidden">
      <button
        type="button"
        aria-expanded={open}
        aria-controls="mobile-navigation"
        onClick={() => setOpen((current) => !current)}
        className="inline-flex h-10 items-center gap-2 rounded border border-line bg-surface px-3 text-sm text-ink transition-colors hover:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
      >
        <span className="sr-only">
          {open ? "Close navigation" : "Open navigation"}
        </span>
        <span aria-hidden="true" className="flex w-4 flex-col gap-1">
          <span className="h-px w-full bg-ink" />
          <span className="h-px w-full bg-ink" />
          <span className="h-px w-full bg-ink" />
        </span>
        <span aria-hidden="true">Menu</span>
      </button>

      {open ? (
        <nav
          id="mobile-navigation"
          aria-label="Mobile"
          className="absolute inset-x-0 top-16 z-40 border-b border-line bg-paper px-4 py-4 animate-fade-in"
        >
          <ul className="space-y-1">
            {[...primaryNav, { href: "/cart", label: "Cart" }].map((item) => {
              const active = pathname === item.href
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className="block rounded px-2 py-3 text-base text-ink transition-colors hover:bg-[#f0ede8] hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                  >
                    {item.label}
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>
      ) : null}
    </div>
  )
}

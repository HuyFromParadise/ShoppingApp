"use client"

import * as React from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { ErrorState } from "@/components/ui/error-state"
import { Skeleton } from "@/components/ui/skeleton"
import { useSession } from "@/features/auth/session"

/**
 * Admin route guard (PROJECT_SPEC §6: "Protect admin routes in the UI for good
 * UX, but always enforce access control in backend APIs").
 *
 * <p>This is UX only. Every admin call is authorized by the backend, and a
 * customer who reaches a page by any means still gets 403s from the API. The
 * guard exists so a signed-out or non-admin visitor is not shown an empty
 * admin shell that can only fail.
 */
export function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { status, user } = useSession()
  const router = useRouter()
  const pathname = usePathname()

  React.useEffect(() => {
    if (status === "unauthenticated") {
      const next = pathname ? `?next=${encodeURIComponent(pathname)}` : ""
      router.replace(`/login${next}`)
    }
  }, [status, router, pathname])

  if (status === "loading") {
    return (
      <div className="space-y-4" role="status">
        <span className="sr-only">Checking your session</span>
        <Skeleton className="h-8 w-56" />
        <Skeleton lines={4} />
      </div>
    )
  }

  if (status === "unauthenticated") {
    return (
      <div role="status">
        <span className="sr-only">Redirecting to sign in</span>
        <Skeleton lines={3} />
      </div>
    )
  }

  if (user?.role !== "ADMIN") {
    return (
      <ErrorState
        title="This area is for store administrators"
        description="Your account does not have admin access. If you believe this is a mistake, contact the store owner."
        action={
          <Link
            href="/"
            className="text-sm text-ink underline underline-offset-4 hover:text-accent"
          >
            Back to the store
          </Link>
        }
      />
    )
  }

  return <>{children}</>
}

"use client"

import * as React from "react"
import { usePathname, useRouter } from "next/navigation"
import { Skeleton } from "@/components/ui/skeleton"
import { useSession } from "./session"

/**
 * Route guards. They only shape the UX — every protected API call is
 * authorized by the backend, never by these components.
 */

function AuthPendingState({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center gap-3 py-16" role="status">
      <span className="sr-only">{label}</span>
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-4 w-64" />
    </div>
  )
}

export function RequireAuth({
  children,
  loginPath = "/login",
}: {
  children: React.ReactNode
  loginPath?: string
}) {
  const { status } = useSession()
  const router = useRouter()
  const pathname = usePathname()

  React.useEffect(() => {
    if (status === "unauthenticated") {
      // Remember where the visitor wanted to go, so signing in returns there.
      const target = pathname ? `${loginPath}?next=${encodeURIComponent(pathname)}` : loginPath
      router.replace(target)
    }
  }, [status, router, loginPath, pathname])

  if (status !== "authenticated") {
    return <AuthPendingState label="Checking your session" />
  }
  return <>{children}</>
}

export function RequireAnonymous({
  children,
  redirectTo = "/",
}: {
  children: React.ReactNode
  redirectTo?: string
}) {
  const { status } = useSession()
  const router = useRouter()

  React.useEffect(() => {
    if (status === "authenticated") {
      router.replace(redirectTo)
    }
  }, [status, router, redirectTo])

  if (status === "authenticated") {
    return <AuthPendingState label="Redirecting" />
  }
  return <>{children}</>
}

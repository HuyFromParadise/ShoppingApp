"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useToast } from "@/components/ui/toast"
import { useLogout } from "@/features/auth/hooks"
import { useSession } from "@/features/auth/session"

/**
 * Header entry point for the session: a sign-in link for visitors, the
 * account name and a sign-out button for signed-in users.
 */
export function AccountMenu() {
  const { status, user } = useSession()
  const logoutMutation = useLogout()
  const router = useRouter()
  const { toast } = useToast()

  if (status === "loading") {
    return null
  }

  if (status === "unauthenticated") {
    return (
      <Link
        href="/login"
        className="text-sm text-ink transition-colors duration-150 hover:text-accent"
      >
        Sign in
      </Link>
    )
  }

  return (
    <div className="flex items-center gap-4">
      <span className="hidden text-sm text-muted sm:inline">
        {user ? user.firstName : null}
      </span>
      {user?.role === "ADMIN" ? (
        <Link
          href="/admin"
          className="text-sm text-ink transition-colors duration-150 hover:text-accent"
        >
          Admin
        </Link>
      ) : null}
      <button
        type="button"
        disabled={logoutMutation.isPending}
        onClick={() => {
          logoutMutation.mutate(undefined, {
            onError: () =>
              toast({
                title: "Sign out did not reach the server",
                description:
                  "Your session may still be active on this device. Try again once you are back online.",
                variant: "error",
              }),
            onSettled: () => router.replace("/"),
          })
        }}
        className="text-sm text-ink transition-colors duration-150 hover:text-accent disabled:opacity-60"
      >
        Sign out
      </button>
    </div>
  )
}

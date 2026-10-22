import type { Metadata } from "next"
import { LoginForm } from "@/features/auth/login-form"
import { safeRedirectPath } from "@/features/auth/redirect"
import { RequireAnonymous } from "@/features/auth/route-guards"

export const metadata: Metadata = {
  title: "Sign in",
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>
}) {
  const params = await searchParams
  const next = safeRedirectPath(params.next)

  return (
    <div className="w-full max-w-md">
      <h1 className="text-2xl font-semibold text-ink">Sign in</h1>
      <p className="mt-2 text-sm text-muted">
        Welcome back. Sign in to reach your cart, orders and saved details.
      </p>
      <div className="mt-8 rounded border border-line bg-surface p-6 sm:p-8">
        <RequireAnonymous>
          <LoginForm next={next} />
        </RequireAnonymous>
      </div>
    </div>
  )
}

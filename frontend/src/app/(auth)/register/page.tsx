import type { Metadata } from "next"
import { RegisterForm } from "@/features/auth/register-form"
import { safeRedirectPath } from "@/features/auth/redirect"
import { RequireAnonymous } from "@/features/auth/route-guards"

export const metadata: Metadata = {
  title: "Create account",
}

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>
}) {
  const params = await searchParams
  const next = safeRedirectPath(params.next)

  return (
    <div className="w-full max-w-md">
      <h1 className="text-2xl font-semibold text-ink">Create account</h1>
      <p className="mt-2 text-sm text-muted">
        One account for orders, addresses and faster checkout.
      </p>
      <div className="mt-8 rounded border border-line bg-surface p-6 sm:p-8">
        <RequireAnonymous>
          <RegisterForm next={next} />
        </RequireAnonymous>
      </div>
    </div>
  )
}

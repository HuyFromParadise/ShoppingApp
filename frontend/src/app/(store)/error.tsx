"use client"

import Link from "next/link"
import { ErrorState } from "@/components/ui/error-state"
import { Button } from "@/components/ui/button"

export default function StoreError({
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <div className="container-store py-16">
      <ErrorState
        title="We could not load this page"
        description="The storefront hit an unexpected problem. You can retry or return to the shop."
        action={
          <>
            <Button variant="secondary" onClick={reset}>
              Try again
            </Button>
            <Link
              href="/shop"
              className="text-sm text-ink underline underline-offset-4"
            >
              Back to shop
            </Link>
          </>
        }
      />
    </div>
  )
}

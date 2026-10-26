"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { useToast } from "@/components/ui/toast"
import { useSession } from "@/features/auth/session"
import { useIsWishlisted, useToggleWishlist } from "@/features/wishlist/hooks"
import { ApiError } from "@/lib/api/client"
import { cn } from "@/lib/utils/cn"

/**
 * Save/unsave control (PROJECT_SPEC §6, P1).
 *
 * <p>A signed-out visitor is sent to sign in and comes straight back, rather
 * than being given a button that can only fail. The label and the pressed state
 * carry the meaning, so saving never depends on colour alone.
 */
export function WishlistButton({
  productId,
  productName,
  className,
}: {
  productId: string
  productName: string
  className?: string
}) {
  const { status } = useSession()
  const signedIn = status === "authenticated"
  const saved = useIsWishlisted(productId, signedIn)
  const toggle = useToggleWishlist()
  const router = useRouter()
  const { toast } = useToast()

  const isSaved = saved.data === true

  function onClick() {
    if (!signedIn) {
      const next = encodeURIComponent(window.location.pathname)
      router.push(`/login?next=${next}`)
      return
    }
    toggle.mutate(
      { productId, saved: isSaved },
      {
        onSuccess: () =>
          toast({
            title: isSaved ? "Removed from your wishlist" : "Saved to your wishlist",
            description: productName,
            variant: "success",
          }),
        onError: (error) =>
          toast({
            title: "We could not update your wishlist",
            description:
              error instanceof ApiError ? error.message : "Please try again.",
            variant: "error",
          }),
      },
    )
  }

  return (
    <Button
      type="button"
      variant="secondary"
      size="sm"
      className={cn("gap-2", className)}
      aria-pressed={signedIn ? isSaved : undefined}
      loading={signedIn && (toggle.isPending || saved.isPending)}
      onClick={onClick}
    >
      <span aria-hidden="true" className="text-base leading-none">
        {isSaved ? "♥" : "♡"}
      </span>
      {isSaved ? "Saved" : "Save"}
    </Button>
  )
}

/** A link to the wishlist with its item count, for the account page. */
export function WishlistLink({ count }: { count: number }) {
  return (
    <Link
      href="/wishlist"
      className="text-sm text-ink underline underline-offset-4 hover:text-accent"
    >
      Your wishlist{count > 0 ? ` (${count})` : ""}
    </Link>
  )
}

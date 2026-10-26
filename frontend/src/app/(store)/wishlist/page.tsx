"use client"

import * as React from "react"
import Link from "next/link"
import { ProductImage } from "@/components/product/product-image"
import { Button, buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { useSession } from "@/features/auth/session"
import { useToggleWishlist, useWishlist } from "@/features/wishlist/hooks"
import { ApiError } from "@/lib/api/client"
import { formatDate } from "@/lib/utils/date"

/**
 * Wishlist page (PROJECT_SPEC §11: {@code /wishlist}).
 *
 * <p>Saved items stay listed after being archived so the customer can see what
 * happened to them; those rows say so and cannot be added to the cart.
 */
export default function WishlistPage() {
  const { status } = useSession()
  return status === "authenticated" ? (
    <WishlistView />
  ) : (
    <SignInPrompt pending={status === "loading"} />
  )
}

function SignInPrompt({ pending }: { pending: boolean }) {
  if (pending) return <Skeleton lines={4} />
  return (
    <EmptyState
      title="Sign in to see your wishlist"
      description="Your saved pieces are kept with your account."
      action={
        <Link href="/login?next=%2Fwishlist" className={buttonVariants()}>
          Sign in
        </Link>
      }
    />
  )
}

function WishlistView() {
  const wishlist = useWishlist()
  const toggle = useToggleWishlist()
  const { toast } = useToast()

  if (wishlist.isPending) return <Skeleton lines={5} />
  if (wishlist.error) {
    return (
      <ErrorState
        title="We could not load your wishlist"
        onRetry={() => void wishlist.refetch()}
      />
    )
  }

  const items = wishlist.data ?? []
  if (items.length === 0) {
    return (
      <EmptyState
        title="Nothing saved yet"
        description="Save a piece from any product page and it will wait for you here."
        action={
          <Link href="/shop" className={buttonVariants()}>
            Browse the shop
          </Link>
        }
      />
    )
  }

  return (
    <section aria-labelledby="wishlist-heading">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 id="wishlist-heading" className="text-lg font-medium tracking-tight text-ink">
          Saved pieces
        </h2>
        <p className="text-xs text-muted" aria-live="polite">
          {items.length} {items.length === 1 ? "item" : "items"}
        </p>
      </div>

      <ul className="mt-4 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((item) => (
          <li key={item.productId} className="border border-line bg-surface">
            <Link
              href={`/products/${encodeURIComponent(item.productSlug)}`}
              className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
            >
              <ProductImage
                src={item.imageUrl}
                alt={item.productName}
                className="aspect-[4/5] w-full"
              />
            </Link>
            <div className="space-y-2 p-4">
              <Link
                href={`/products/${encodeURIComponent(item.productSlug)}`}
                className="block font-medium text-ink hover:underline hover:underline-offset-4"
              >
                {item.productName}
              </Link>
              <PriceDisplay
                amountMinor={item.priceMinor}
                compareAtMinor={item.compareAtPriceMinor ?? undefined}
                currency={item.currency}
                size="sm"
              />
              {/* Availability is stated in words, not by colour alone. */}
              <p className="text-xs text-muted">
                {item.purchasable
                  ? item.inStock
                    ? "In stock"
                    : "Currently out of stock"
                  : "No longer sold"}
              </p>
              {item.savedAt ? (
                <p className="text-xs text-muted">Saved {formatDate(item.savedAt)}</p>
              ) : null}
              <div className="flex flex-wrap items-center gap-2 pt-1">
                {item.purchasable ? (
                  <Link
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className={buttonVariants({ size: "sm" })}
                  >
                    {item.inStock ? "Choose options" : "View product"}
                  </Link>
                ) : null}
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  loading={toggle.isPending}
                  onClick={() =>
                    toggle.mutate(
                      { productId: item.productId, saved: true },
                      {
                        onSuccess: () =>
                          toast({
                            title: "Removed from your wishlist",
                            description: item.productName,
                          }),
                        onError: (error) =>
                          toast({
                            title: "We could not update your wishlist",
                            description:
                              error instanceof ApiError ? error.message : "Try again.",
                            variant: "error",
                          }),
                      },
                    )
                  }
                >
                  Remove<span className="sr-only"> {item.productName}</span>
                </Button>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

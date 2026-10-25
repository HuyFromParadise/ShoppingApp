"use client"

import * as React from "react"
import Link from "next/link"
import { QuantityStepper } from "@/components/cart/quantity-stepper"
import { ProductImage } from "@/components/product/product-image"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { RequireAuth } from "@/features/auth/route-guards"
import {
  useCart,
  useClearCart,
  useRemoveCartItem,
  useUpdateCartItem,
} from "@/features/cart/hooks"
import { ApiError } from "@/lib/api/client"
import { formatMoney } from "@/lib/utils/money"
import { MAX_LINE_QUANTITY } from "@/types/cart"

/**
 * Cart page (DESIGN_REFERENCE §9). Cart data is authenticated, so it is fetched
 * client-side; the guard only shapes the UX and the backend authorizes every
 * call. Totals and availability come from the server on every change.
 */
export default function CartPage() {
  return (
    <RequireAuth>
      <CartView />
    </RequireAuth>
  )
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.message ? error.message : fallback
}

function CartView() {
  const { data: cart, isPending, error, refetch } = useCart()
  const updateItem = useUpdateCartItem()
  const removeItem = useRemoveCartItem()
  const clearCart = useClearCart()
  const { toast } = useToast()

  if (isPending) {
    return (
      <div className="container-store py-10 sm:py-14">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Your cart
        </h1>
        <div className="mt-8">
          <Skeleton lines={5} />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="container-store py-10 sm:py-14">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Your cart
        </h1>
        <div className="mt-8">
          <ErrorState
            title="We could not load your cart"
            onRetry={() => void refetch()}
          />
        </div>
      </div>
    )
  }

  const items = cart?.items ?? []
  // Checkout re-validates everything, but sending the customer to a page that
  // can only fail is worse than asking them to fix the cart first.
  const hasUnavailable = items.some((item) => !item.available)

  function updateQuantity(itemId: string, quantity: number) {
    updateItem.mutate(
      { itemId, quantity },
      {
        onError: (failure) =>
          toast({
            title: "Could not update the quantity",
            description: messageOf(failure, "Please try again."),
            variant: "error",
          }),
      },
    )
  }

  function remove(itemId: string) {
    removeItem.mutate(itemId, {
      onError: (failure) =>
        toast({
          title: "Could not remove this item",
          description: messageOf(failure, "Please try again."),
          variant: "error",
        }),
    })
  }

  function clear() {
    if (!window.confirm("Remove all items from your cart?")) return
    clearCart.mutate(undefined, {
      onError: (failure) =>
        toast({
          title: "Could not clear your cart",
          description: messageOf(failure, "Please try again."),
          variant: "error",
        }),
    })
  }

  return (
    <div className="container-store py-10 sm:py-14">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">
        Your cart
      </h1>

      {!cart || items.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            title="Your cart is empty"
            description="Browse the shop and add a piece to get started."
            action={
              <Link href="/shop" className={buttonVariants()}>
                Continue shopping
              </Link>
            }
          />
        </div>
      ) : (
        <div className="mt-8 grid gap-10 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <section aria-labelledby="cart-items">
            <h2 id="cart-items" className="sr-only">
              Items
            </h2>
            <ul className="divide-y divide-line border-y border-line">
              {items.map((item) => {
                const productPath = `/products/${encodeURIComponent(item.productSlug)}`
                const attributes =
                  [item.color, item.size].filter(Boolean).join(" / ") || item.sku

                return (
                  <li
                    key={item.id}
                    className="flex gap-4 py-6 sm:gap-6"
                    aria-label={item.productName}
                  >
                    <Link href={productPath} className="block w-20 shrink-0 sm:w-24">
                      <ProductImage
                        src={item.imageUrl}
                        alt={item.productName}
                        className="aspect-[3/4] w-full"
                      />
                    </Link>

                    <div className="flex min-w-0 flex-1 flex-col gap-3">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <Link
                            href={productPath}
                            className="font-medium text-ink transition-colors duration-150 hover:text-accent"
                          >
                            {item.productName}
                          </Link>
                          <p className="mt-1 text-xs uppercase tracking-label text-muted">
                            {attributes}
                          </p>
                          {!item.available ? (
                            <Badge variant="accent" className="mt-2">
                              No longer available
                            </Badge>
                          ) : null}
                        </div>
                        <PriceDisplay
                          amountMinor={item.lineTotalMinor}
                          currency={cart.currency}
                        />
                      </div>

                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="flex flex-wrap items-center gap-4">
                          <QuantityStepper
                            value={item.quantity}
                            max={Math.min(MAX_LINE_QUANTITY, item.stockQuantity)}
                            disabled={
                              item.stockQuantity < 1 ||
                              (updateItem.isPending &&
                                updateItem.variables?.itemId === item.id)
                            }
                            onChange={(quantity) =>
                              updateQuantity(item.id, quantity)
                            }
                          />
                          <p className="text-xs text-muted">
                            {formatMoney(item.unitPriceMinor, cart.currency)} each
                            {item.stockQuantity > 0
                              ? ` · ${item.stockQuantity} in stock`
                              : null}
                          </p>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={removeItem.isPending}
                          onClick={() => remove(item.id)}
                        >
                          Remove
                          <span className="sr-only"> {item.productName}</span>
                        </Button>
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          </section>

          <aside
            aria-labelledby="cart-summary"
            className="h-fit border border-line bg-surface p-6 lg:sticky lg:top-24"
          >
            <h2
              id="cart-summary"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Summary
            </h2>
            <dl className="mt-4 space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Items</dt>
                <dd className="tabular-nums text-ink">{cart.itemCount}</dd>
              </div>
              <div className="flex justify-between gap-4 border-t border-line pt-2">
                <dt className="text-ink">Subtotal</dt>
                <dd>
                  <PriceDisplay
                    amountMinor={cart.subtotalMinor}
                    currency={cart.currency}
                  />
                </dd>
              </div>
            </dl>
            <div className="mt-6 flex flex-col gap-3">
              <Link
                href="/checkout"
                aria-disabled={hasUnavailable || undefined}
                onClick={
                  hasUnavailable
                    ? (event) => {
                        event.preventDefault()
                        toast({
                          title: "Remove the unavailable items first",
                          description:
                            "An item in your cart can no longer be bought.",
                          variant: "error",
                        })
                      }
                    : undefined
                }
                className={buttonVariants({
                  className: hasUnavailable ? "cursor-not-allowed opacity-50" : undefined,
                })}
              >
                Proceed to checkout
              </Link>
              <Link
                href="/shop"
                className={buttonVariants({ variant: "secondary" })}
              >
                Continue shopping
              </Link>
              <Button
                variant="ghost"
                size="sm"
                disabled={clearCart.isPending}
                onClick={clear}
              >
                Clear cart
              </Button>
            </div>
          </aside>
        </div>
      )}
    </div>
  )
}

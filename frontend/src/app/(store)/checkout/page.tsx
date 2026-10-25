"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Button, buttonVariants } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { useAddresses } from "@/features/account/hooks"
import {
  describeQuoteError,
  ShippingChoice,
} from "@/features/checkout/shipping-choice"
import { QuoteTotals } from "@/features/checkout/totals"
import { useCart } from "@/features/cart/hooks"
import { usePlaceOrder, useQuote } from "@/features/orders/hooks"
import { CheckoutAttempt } from "@/features/orders/workflow"
import { ApiError } from "@/lib/api/client"
import { formatMoney } from "@/lib/utils/money"
import type { Address } from "@/types/account"
import {
  MAX_CUSTOMER_NOTE_LENGTH,
  type CreateOrderInput,
  type QuoteInput,
  type ShippingMethodCode,
} from "@/types/orders"

/**
 * Checkout (DESIGN_REFERENCE §5.3, §9). The page never computes a total: it
 * collects the two inputs the quote endpoint needs and renders what the server
 * returns. The order is posted with one `Idempotency-Key` per attempt, so a
 * retry after a network failure cannot create a second order.
 */
export default function CheckoutPage() {
  const router = useRouter()
  const cart = useCart()
  const addresses = useAddresses()
  const placeOrder = usePlaceOrder()
  const { toast } = useToast()

  const [addressId, setAddressId] = React.useState<string | null>(null)
  const [shippingMethod, setShippingMethod] =
    React.useState<ShippingMethodCode>("STANDARD")
  const [note, setNote] = React.useState("")

  // One key per attempt. It is reused across retries of the same request and
  // replaced when the request changes, because the backend rejects a reused key
  // with a different body (409 IDEMPOTENCY_KEY_REUSED).
  const attempt = React.useRef<CheckoutAttempt | null>(null)

  React.useEffect(() => {
    if (addressId || !addresses.data || addresses.data.length === 0) return
    const preferred =
      addresses.data.find((address) => address.isDefault) ?? addresses.data[0]
    setAddressId(preferred.id)
  }, [addressId, addresses.data])

  const quoteInput: QuoteInput | null = addressId
    ? { addressId, shippingMethod }
    : null
  const quote = useQuote(quoteInput)

  const items = cart.data?.items ?? []
  const cartCurrency = cart.data?.currency ?? "USD"
  const loading = cart.isPending || addresses.isPending

  function place() {
    if (!addressId || !quote.data || placeOrder.isPending) return

    const input: CreateOrderInput = {
      addressId,
      shippingMethod,
      customerNote: note.trim() || undefined,
    }
    attempt.current ??= new CheckoutAttempt()
    const idempotencyKey = attempt.current.keyFor(input)

    placeOrder.mutate(
      { input, idempotencyKey },
      {
        onSuccess: (order) => {
          attempt.current?.reset()
          router.push(`/account/orders/${order.id}?placed=1`)
        },
        onError: (failure) => {
          // A rejected request has a different outcome from a retry of the same
          // one, so the next click must start a fresh attempt.
          if (
            failure instanceof ApiError &&
            (failure.code === "OUT_OF_STOCK" ||
              failure.code === "CONFLICT" ||
              failure.code === "VALIDATION_ERROR" ||
              failure.code === "IDEMPOTENCY_KEY_REUSED")
          ) {
            attempt.current?.reset()
            void quote.refetch()
          }
          toast({
            title: "We could not place your order",
            description: describeQuoteError(failure).message,
            variant: "error",
          })
        },
      },
    )
  }

  if (loading) {
    return (
      <div className="container-store py-10 sm:py-14">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Checkout
        </h1>
        <div className="mt-8">
          <Skeleton lines={6} />
        </div>
      </div>
    )
  }

  if (cart.error || addresses.error) {
    return (
      <div className="container-store py-10 sm:py-14">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Checkout
        </h1>
        <div className="mt-8">
          <ErrorState
            title="We could not load your checkout"
            onRetry={() => {
              void cart.refetch()
              void addresses.refetch()
            }}
          />
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="container-store py-10 sm:py-14">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Checkout
        </h1>
        <p className="mt-3 text-sm text-muted">Your cart is empty.</p>
        <Link href="/shop" className={`${buttonVariants()} mt-6`}>
          Continue shopping
        </Link>
      </div>
    )
  }

  const list = addresses.data ?? []
  const unavailable = items.filter((item) => !item.available)
  const quoteProblem = quote.error ? describeQuoteError(quote.error) : null

  return (
    <div className="container-store py-10 sm:py-14">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">
        Checkout
      </h1>

      <div className="mt-8 grid gap-10 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-10">
          <section aria-labelledby="checkout-address">
            <h2
              id="checkout-address"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Delivery address
            </h2>
            {list.length === 0 ? (
              <div className="mt-4 border border-line bg-surface p-6">
                <p className="text-sm text-muted">
                  Add a delivery address to continue.
                </p>
                <Link
                  href="/account/addresses"
                  className={`${buttonVariants({ variant: "secondary", size: "sm" })} mt-4`}
                >
                  Add an address
                </Link>
              </div>
            ) : (
              <ul className="mt-4 grid gap-3 sm:grid-cols-2">
                {list.map((address) => (
                  <li key={address.id}>
                    <AddressOption
                      address={address}
                      selected={addressId === address.id}
                      onSelect={() => setAddressId(address.id)}
                    />
                  </li>
                ))}
              </ul>
            )}
          </section>

          <ShippingChoice
            value={shippingMethod}
            onChange={setShippingMethod}
            disabled={placeOrder.isPending}
          />

          <section aria-labelledby="checkout-items">
            <h2
              id="checkout-items"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Items
            </h2>
            <ul className="mt-4 divide-y divide-line border-y border-line">
              {items.map((item) => {
                const attributes =
                  [item.color, item.size].filter(Boolean).join(" / ") || item.sku
                return (
                  <li
                    key={item.id}
                    className="flex items-center justify-between gap-4 py-4 text-sm"
                  >
                    <div className="min-w-0">
                      <p className="font-medium text-ink">{item.productName}</p>
                      <p className="mt-1 text-xs uppercase tracking-label text-muted">
                        {attributes} · ×{item.quantity}
                      </p>
                      {!item.available ? (
                        <p className="mt-1 text-xs text-accent">
                          No longer available — remove it from your cart.
                        </p>
                      ) : null}
                    </div>
                    <PriceDisplay
                      amountMinor={item.lineTotalMinor}
                      currency={cartCurrency}
                    />
                  </li>
                )
              })}
            </ul>
          </section>

          <section aria-labelledby="checkout-note">
            <label
              htmlFor="customer-note"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Delivery note <span className="normal-case">(optional)</span>
            </label>
            <textarea
              id="customer-note"
              value={note}
              onChange={(event) => setNote(event.target.value)}
              maxLength={MAX_CUSTOMER_NOTE_LENGTH}
              rows={3}
              disabled={placeOrder.isPending}
              className="mt-2 w-full rounded border border-line bg-surface px-3 py-2 text-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
              placeholder="Anything the courier should know?"
            />
          </section>
        </div>

        <aside
          aria-labelledby="checkout-summary"
          className="h-fit border border-line bg-surface p-6 lg:sticky lg:top-24"
        >
          <h2
            id="checkout-summary"
            className="text-xs font-medium uppercase tracking-label text-muted"
          >
            Order summary
          </h2>

          <div className="mt-4">
            {quote.isPending && quoteInput ? (
              <Skeleton lines={4} />
            ) : quoteProblem ? (
              <div role="alert" className="text-sm">
                <p className="font-medium text-ink">{quoteProblem.title}</p>
                <p className="mt-1 text-muted">{quoteProblem.message}</p>
                <Button
                  variant="secondary"
                  size="sm"
                  className="mt-3"
                  onClick={() => void quote.refetch()}
                >
                  Recalculate
                </Button>
              </div>
            ) : quote.data ? (
              <QuoteTotals quote={quote.data} />
            ) : (
              <p className="text-sm text-muted">
                Choose an address to see the total.
              </p>
            )}
          </div>

          <div className="mt-6 space-y-3">
            <Button
              className="w-full"
              size="lg"
              loading={placeOrder.isPending}
              disabled={
                !quote.data ||
                unavailable.length > 0 ||
                Boolean(quoteProblem) ||
                list.length === 0
              }
              onClick={place}
            >
              Place order
            </Button>
            <p className="text-xs text-muted">
              The total is calculated by our server.{" "}
              {quote.data
                ? `You will pay ${formatMoney(quote.data.grandTotalMinor, quote.data.currency)}.`
                : null}
            </p>
          </div>
        </aside>
      </div>
    </div>
  )
}

function AddressOption({
  address,
  selected,
  onSelect,
}: {
  address: Address
  selected: boolean
  onSelect: () => void
}) {
  return (
    <label
      className={`flex h-full cursor-pointer gap-3 rounded border p-4 text-sm ${
        selected ? "border-ink" : "border-line"
      } bg-surface`}
    >
      <input
        type="radio"
        name="addressId"
        value={address.id}
        checked={selected}
        onChange={onSelect}
        className="mt-0.5 accent-[var(--color-ink)]"
      />
      <span className="min-w-0">
        <span className="block font-medium text-ink">{address.recipientName}</span>
        <span className="mt-1 block text-muted">
          {address.addressLine1}
          {address.addressLine2 ? `, ${address.addressLine2}` : ""}
        </span>
        <span className="block text-muted">
          {[address.ward, address.district, address.cityOrProvince]
            .filter(Boolean)
            .join(", ")}
        </span>
        {address.phone ? (
          <span className="mt-1 block text-xs text-muted">{address.phone}</span>
        ) : null}
      </span>
    </label>
  )
}

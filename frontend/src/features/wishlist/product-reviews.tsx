"use client"

import * as React from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { useSession } from "@/features/auth/session"
import {
  useCreateReview,
  useProductReviews,
  useReviewEligibility,
} from "@/features/wishlist/hooks"
import { ApiError } from "@/lib/api/client"
import { formatDate } from "@/lib/utils/date"
import type { ProductReviews } from "@/types/p1"

/** A rating shown as text as well as stars, so it never relies on colour. */
export function RatingStars({
  rating,
  className,
}: {
  rating: number
  className?: string
}) {
  const rounded = Math.round(rating)
  return (
    <span className={className}>
      <span aria-hidden="true" className="tracking-[0.15em] text-ink">
        {"★".repeat(rounded)}
        <span className="text-line">{"★".repeat(Math.max(0, 5 - rounded))}</span>
      </span>
      <span className="sr-only">{rating} out of 5 stars</span>
    </span>
  )
}

/**
 * Reviews section of the product page (PROJECT_SPEC §7 Review).
 *
 * <p>Reviews are public; writing one needs a signed-in customer. The form is
 * only offered when the API says the customer may still review, and the server
 * checks again — the eligibility call shapes the UI, it is not the gate.
 */
export function ProductReviews({
  slug,
  productName,
}: {
  slug: string
  productName: string
}) {
  const reviews = useProductReviews(slug)

  if (reviews.isPending) {
    return (
      <section className="mt-10 border-t border-line pt-8" aria-labelledby="reviews-heading">
        <h2 id="reviews-heading" className="text-sm font-medium text-ink">
          Reviews
        </h2>
        <div className="mt-4">
          <Skeleton lines={3} />
        </div>
      </section>
    )
  }

  if (reviews.error) {
    return (
      <section className="mt-10 border-t border-line pt-8" aria-labelledby="reviews-heading">
        <h2 id="reviews-heading" className="text-sm font-medium text-ink">
          Reviews
        </h2>
        <div className="mt-4">
          <ErrorState
            title="Reviews could not be loaded"
            onRetry={() => void reviews.refetch()}
          />
        </div>
      </section>
    )
  }

  const data = reviews.data
  if (!data) return null

  return (
    <section
      className="mt-10 border-t border-line pt-8"
      aria-labelledby="reviews-heading"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 id="reviews-heading" className="text-sm font-medium text-ink">
          Reviews
        </h2>
        <p className="text-xs text-muted" aria-live="polite">
          {data.reviewCount === 0
            ? "No reviews yet"
            : `${data.averageRating} out of 5 from ${data.reviewCount} ${
                data.reviewCount === 1 ? "review" : "reviews"
              }`}
        </p>
      </div>

      <ReviewBreakdown data={data} />
      <ReviewForm slug={slug} productName={productName} />

      {data.items.length === 0 ? (
        <p className="mt-6 text-sm text-muted">
          Be the first to review {productName}.
        </p>
      ) : (
        <ul className="mt-6 divide-y divide-line border-t border-line">
          {data.items.map((review) => (
            <li key={review.id} className="py-5">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <RatingStars rating={review.rating} className="text-sm" />
                <span className="text-sm font-medium text-ink">
                  {review.title ?? "Review"}
                </span>
                {review.verifiedPurchase ? (
                  <span className="rounded-sm border border-[#c9dfd3] bg-[#e8f1ec] px-2 py-0.5 text-xs font-medium text-success">
                    Verified purchase
                  </span>
                ) : null}
              </div>
              {review.comment ? (
                <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-muted">
                  {review.comment}
                </p>
              ) : null}
              <p className="mt-2 text-xs text-muted">
                {review.authorName}
                {review.createdAt ? ` · ${formatDate(review.createdAt)}` : ""}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function ReviewBreakdown({ data }: { data: ProductReviews }) {
  if (data.reviewCount === 0) return null
  const total = data.reviewCount
  return (
    <dl className="mt-4 space-y-1">
      {[5, 4, 3, 2, 1].map((stars) => {
        const count = data.ratingCounts[stars - 1] ?? 0
        const percent = total === 0 ? 0 : Math.round((count / total) * 100)
        return (
          <div key={stars} className="flex items-center gap-3 text-xs text-muted">
            <dt className="w-14 shrink-0 tabular-nums">{stars} star{stars === 1 ? "" : "s"}</dt>
            <dd className="flex flex-1 items-center gap-3">
              <span
                aria-hidden="true"
                className="h-1.5 flex-1 bg-[#f0ede8]"
              >
                <span
                  className="block h-full bg-ink"
                  style={{ width: `${percent}%` }}
                />
              </span>
              <span className="w-10 shrink-0 text-right tabular-nums">{count}</span>
              <span className="sr-only">
                {count} of {total} reviews rated {stars} stars
              </span>
            </dd>
          </div>
        )
      })}
    </dl>
  )
}

function ReviewForm({ slug, productName }: { slug: string; productName: string }) {
  const { status } = useSession()
  const signedIn = status === "authenticated"
  const eligibility = useReviewEligibility(slug, signedIn)
  const create = useCreateReview(slug)
  const { toast } = useToast()

  const [open, setOpen] = React.useState(false)
  const [rating, setRating] = React.useState(5)
  const [title, setTitle] = React.useState("")
  const [comment, setComment] = React.useState("")
  const [error, setError] = React.useState<string | null>(null)

  if (!signedIn) {
    return (
      <p className="mt-4 text-sm text-muted">
        <Link
          href={`/login?next=${encodeURIComponent(`/products/${slug}`)}`}
          className="text-ink underline underline-offset-4 hover:text-accent"
        >
          Sign in
        </Link>{" "}
        to review {productName}.
      </p>
    )
  }

  if (eligibility.data?.canReview === false) {
    return (
      <p className="mt-4 text-sm text-muted">
        You have already reviewed {productName}. Thank you.
      </p>
    )
  }

  if (!open) {
    return (
      <Button className="mt-4" size="sm" variant="secondary" onClick={() => setOpen(true)}>
        Write a review
      </Button>
    )
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    create.mutate(
      {
        rating,
        title: title.trim() || undefined,
        comment: comment.trim() || undefined,
      },
      {
        onSuccess: () => {
          setOpen(false)
          setTitle("")
          setComment("")
          setRating(5)
          toast({
            title: "Thank you for your review",
            description: "It is now visible on this page.",
            variant: "success",
          })
        },
        onError: (failure) => {
          if (failure instanceof ApiError) {
            setError(Object.values(failure.fieldErrors ?? {})[0] ?? failure.message)
          } else {
            setError("We could not save your review. Please try again.")
          }
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="mt-4 space-y-4 border border-line bg-surface p-4">
      <fieldset>
        <legend className="text-sm font-medium text-ink">Your rating</legend>
        <div className="mt-2 flex gap-1">
          {[1, 2, 3, 4, 5].map((value) => (
            <label
              key={value}
              className="cursor-pointer rounded px-1 text-xl focus-within:ring-2 focus-within:ring-accent"
            >
              <input
                type="radio"
                name="rating"
                value={value}
                checked={rating === value}
                onChange={() => setRating(value)}
                className="sr-only"
              />
              <span aria-hidden="true" className={value <= rating ? "text-ink" : "text-line"}>
                ★
              </span>
              <span className="sr-only">{value} stars</span>
            </label>
          ))}
        </div>
      </fieldset>
      <FormField id="review-title" label="Title" hint="Optional">
        <Input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={200}
        />
      </FormField>
      <div className="space-y-1.5">
        <label htmlFor="review-comment" className="block text-sm font-medium text-ink">
          Review
          <span className="ml-1 text-xs font-normal text-muted">
            optional, up to 2000 characters
          </span>
        </label>
        <textarea
          id="review-comment"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          maxLength={2000}
          rows={4}
          className="w-full rounded border border-line bg-surface px-3 py-2 text-sm text-ink focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        />
      </div>
      {error ? (
        <p role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" loading={create.isPending}>
          Publish review
        </Button>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={create.isPending}
          onClick={() => setOpen(false)}
        >
          Cancel
        </Button>
      </div>
    </form>
  )
}

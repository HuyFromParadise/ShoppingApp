"use client"

import * as React from "react"
import { useToast } from "@/components/ui/toast"
import { useDeleteImage, useUploadImage } from "@/features/admin/hooks"
import { mediaUrl } from "@/lib/api/client"
import type { AdminImage } from "@/types/admin"

const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED = ["image/jpeg", "image/png", "image/webp"]

/**
 * Product images (PROJECT_SPEC §7: multipart, validated). The browser check is
 * a convenience; the server sniffs the bytes and decides the real type, so a
 * renamed file still fails there.
 *
 * <p>The API stores a root-relative key (`/media/products/…`) served by the
 * backend origin, so it is resolved through {@link mediaUrl} before it reaches
 * `src`; rendering the raw key would ask the frontend origin for a file it does
 * not have.
 */
export function ProductImages({
  productId,
  images,
}: {
  productId: string
  images: readonly AdminImage[]
}) {
  const upload = useUploadImage(productId)
  const remove = useDeleteImage(productId)
  const { toast } = useToast()
  const inputRef = React.useRef<HTMLInputElement>(null)
  const [altText, setAltText] = React.useState("")
  const [error, setError] = React.useState<string | null>(null)
  const [busy, setBusy] = React.useState(false)

  async function handleFile(file: File | undefined) {
    setError(null)
    if (!file) return
    if (!ACCEPTED.includes(file.type)) {
      setError("Choose a JPEG, PNG or WebP image.")
      return
    }
    if (file.size > MAX_BYTES) {
      setError("Images must be 5 MB or smaller.")
      return
    }
    setBusy(true)
    upload.mutate(
      { file, altText },
      {
        onSuccess: () => {
          setAltText("")
          if (inputRef.current) inputRef.current.value = ""
          toast({ title: "Image added", variant: "success" })
        },
        onError: (failure) =>
          setError(
            failure instanceof Error ? failure.message : "Could not upload the image.",
          ),
        onSettled: () => setBusy(false),
      },
    )
  }

  return (
    <section aria-labelledby="images-heading" className="space-y-4">
      <h2
        id="images-heading"
        className="text-xs font-medium uppercase tracking-label text-muted"
      >
        Images
      </h2>

      {images.length === 0 ? (
        <p className="text-sm text-muted">No images yet. Add at least one.</p>
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {images.map((image) => (
            <li key={image.id} className="border border-line bg-surface">
              <div className="aspect-[3/4] bg-[#f5f3f0]">
                {mediaUrl(image.url) ? (
                  /* eslint-disable-next-line @next/next/no-img-element */
                  <img
                    src={mediaUrl(image.url) as string}
                    alt={image.altText ?? ""}
                    className="h-full w-full object-cover"
                    // A missing file must not look like a broken layout.
                    onError={(event) => {
                      event.currentTarget.style.visibility = "hidden"
                    }}
                  />
                ) : (
                  <p className="flex h-full items-center justify-center text-xs text-muted">
                    No file
                  </p>
                )}
              </div>
              <div className="flex items-center justify-between gap-2 p-2">
                <span className="text-xs text-muted">
                  {image.primary ? "Primary" : `#${image.sortOrder + 1}`}
                </span>
                <button
                  type="button"
                  className="text-xs text-accent underline underline-offset-4 disabled:opacity-50"
                  disabled={remove.isPending}
                  onClick={() =>
                    remove.mutate(image.id, {
                      onSuccess: () => toast({ title: "Image removed", variant: "success" }),
                      onError: (failure) =>
                        setError(
                          failure instanceof Error ? failure.message : "Could not remove the image.",
                        ),
                    })
                  }
                >
                  Remove
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <div className="space-y-3 border border-line bg-surface p-4">
        <label className="block space-y-1">
          <span className="block text-xs text-muted">Alt text (optional)</span>
          <input
            value={altText}
            onChange={(event) => setAltText(event.target.value)}
            maxLength={200}
            className="h-9 w-full rounded border border-line bg-surface px-2 text-sm text-ink focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            placeholder="Describes the image for screen readers"
          />
        </label>
        <div className="flex flex-wrap items-center gap-3">
          <input
            ref={inputRef}
            id="product-image-file"
            type="file"
            accept={ACCEPTED.join(",")}
            aria-label="Choose an image to upload"
            className="text-sm text-muted file:mr-3 file:rounded file:border file:border-line file:bg-surface file:px-3 file:py-1.5 file:text-sm file:text-ink"
            // A picked file uploads immediately: there is no separate save step.
            onChange={(event) => handleFile(event.target.files?.[0])}
          />
        </div>
        {busy ? (
          <p role="status" className="text-xs text-muted">
            Uploading…
          </p>
        ) : null}
        <p className="text-xs text-muted">JPEG, PNG or WebP, up to 5 MB.</p>
        {error ? (
          <p role="alert" className="text-xs text-accent">
            {error}
          </p>
        ) : null}
      </div>
    </section>
  )
}

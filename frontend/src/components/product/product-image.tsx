"use client"

/* eslint-disable @next/next/no-img-element */
import * as React from "react"
import { mediaUrl } from "@/lib/api/client"
import { cn } from "@/lib/utils/cn"

export interface ProductImageProps {
  src?: string | null
  alt: string
  className?: string
  imgClassName?: string
  sizes?: string
}

/**
 * Product imagery. The catalog API returns a storage key rooted at the backend
 * (`/media/products/…`), so it is resolved through {@link mediaUrl} rather than
 * being requested from the frontend origin, where the file does not exist.
 * A missing or failing image falls back to a quiet neutral placeholder.
 */
export function ProductImage({
  src,
  alt,
  className,
  imgClassName,
  sizes,
}: ProductImageProps) {
  const [failed, setFailed] = React.useState(false)
  const resolved = mediaUrl(src)

  React.useEffect(() => {
    setFailed(false)
  }, [src])

  if (!resolved || failed) {
    return (
      <div
        role="img"
        aria-label={alt}
        className={cn(
          "flex items-center justify-center bg-[#f1efec] text-center",
          className,
        )}
      >
        <span className="px-4 text-[10px] font-medium uppercase tracking-label text-muted">
          Image unavailable
        </span>
      </div>
    )
  }

  return (
    <img
      src={resolved}
      alt={alt}
      sizes={sizes}
      loading="lazy"
      onError={() => setFailed(true)}
      className={cn("h-full w-full object-cover", className, imgClassName)}
    />
  )
}

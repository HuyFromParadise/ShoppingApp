"use client"

/* eslint-disable @next/next/no-img-element */
import * as React from "react"
import { cn } from "@/lib/utils/cn"

export interface ProductImageProps {
  src?: string | null
  alt: string
  className?: string
  imgClassName?: string
  sizes?: string
}

/**
 * Product imagery is currently a storage key from the catalog API. Until the
 * image-serving phase lands, failures fall back to a quiet neutral placeholder.
 */
export function ProductImage({
  src,
  alt,
  className,
  imgClassName,
  sizes,
}: ProductImageProps) {
  const [failed, setFailed] = React.useState(false)

  React.useEffect(() => {
    setFailed(false)
  }, [src])

  if (!src || failed) {
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
      src={src}
      alt={alt}
      sizes={sizes}
      loading="lazy"
      onError={() => setFailed(true)}
      className={cn("h-full w-full object-cover", className, imgClassName)}
    />
  )
}

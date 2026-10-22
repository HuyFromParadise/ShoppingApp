"use client"

import * as React from "react"
import { cn } from "@/lib/utils/cn"
import type { ProductImage as ProductImageDto } from "@/types/catalog"
import { ProductImage } from "./product-image"

export interface ProductGalleryProps {
  images: ProductImageDto[]
  productName: string
}

export function ProductGallery({ images, productName }: ProductGalleryProps) {
  const [selectedIndex, setSelectedIndex] = React.useState(0)
  const selected = images[selectedIndex] ?? images[0]
  const alt = selected?.altText?.trim() || productName

  return (
    <div className="space-y-3">
      <div className="relative aspect-[4/5] overflow-hidden border border-line bg-surface">
        <ProductImage
          key={selected?.id ?? "placeholder"}
          src={selected?.url}
          alt={alt}
          className="h-full w-full"
          sizes="(min-width: 1024px) 50vw, 100vw"
        />
      </div>

      {images.length > 1 ? (
        <div className="grid grid-cols-4 gap-3">
          {images.map((image, index) => {
            const imageAlt =
              image.altText?.trim() || `${productName}, view ${index + 1}`
            return (
              <button
                key={image.id}
                type="button"
                onClick={() => setSelectedIndex(index)}
                aria-label={`View image ${index + 1} of ${images.length}`}
                aria-current={index === selectedIndex ? "true" : undefined}
                className={cn(
                  "relative aspect-square overflow-hidden border bg-surface transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2",
                  index === selectedIndex
                    ? "border-ink"
                    : "border-line hover:border-muted",
                )}
              >
                <ProductImage
                  src={image.url}
                  alt={imageAlt}
                  className="h-full w-full"
                  sizes="15vw"
                />
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}

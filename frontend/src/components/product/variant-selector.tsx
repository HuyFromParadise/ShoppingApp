"use client"

import * as React from "react"
import { PriceDisplay } from "@/components/ui/price-display"
import { cn } from "@/lib/utils/cn"
import type { ProductVariant } from "@/types/catalog"
import { StockIndicator } from "./stock-indicator"

export interface VariantSelectorProps {
  variants: ProductVariant[]
  className?: string
}

function uniqueValues(values: Array<string | null>): string[] {
  return Array.from(
    new Set(values.filter((value): value is string => Boolean(value))),
  )
}

function optionClass(selected: boolean, disabled: boolean): string {
  return cn(
    "inline-flex min-h-10 items-center justify-center rounded border px-4 py-2 text-sm transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2",
    selected
      ? "border-ink bg-ink text-surface"
      : "border-line bg-surface text-ink hover:border-ink",
    disabled && "cursor-not-allowed border-line bg-[#f5f3f0] text-muted opacity-60 hover:border-line",
  )
}

/**
 * Two-dimensional variant picker (colour × size). Combinations without an
 * active in-stock variant are visible but disabled; the selected variant's
 * authoritative price and stock are shown below.
 */
export function VariantSelector({ variants, className }: VariantSelectorProps) {
  const colors = uniqueValues(variants.map((variant) => variant.color))
  const sizes = uniqueValues(variants.map((variant) => variant.size))

  const [selectedColor, setSelectedColor] = React.useState<string | null>(
    colors.length === 1 ? colors[0] : null,
  )
  const [selectedSize, setSelectedSize] = React.useState<string | null>(
    sizes.length === 1 ? sizes[0] : null,
  )

  const colorRequired = colors.length > 0
  const sizeRequired = sizes.length > 0

  const selectedVariant = React.useMemo(() => {
    if (colorRequired && !selectedColor) return null
    if (sizeRequired && !selectedSize) return null
    return (
      variants.find(
        (variant) =>
          (!colorRequired || variant.color === selectedColor) &&
          (!sizeRequired || variant.size === selectedSize),
      ) ?? null
    )
  }, [
    colorRequired,
    selectedColor,
    selectedSize,
    sizeRequired,
    variants,
  ])

  function isColorAvailable(color: string): boolean {
    return variants.some(
      (variant) =>
        variant.color === color &&
        (!sizeRequired || !selectedSize || variant.size === selectedSize) &&
        variant.inStock,
    )
  }

  function isSizeAvailable(size: string): boolean {
    return variants.some(
      (variant) =>
        variant.size === size &&
        (!colorRequired || !selectedColor || variant.color === selectedColor) &&
        variant.inStock,
    )
  }

  const colorLabelId = React.useId()
  const sizeLabelId = React.useId()

  const selectionMessage = selectedVariant
    ? `${selectedVariant.color ?? "Default"} / ${selectedVariant.size ?? "One size"}, ${
        selectedVariant.inStock ? "in stock" : "out of stock"
      }`
    : "Select a variant"

  return (
    <div className={cn("space-y-6", className)}>
      {colorRequired ? (
        <fieldset>
          <legend className="text-xs font-medium uppercase tracking-label text-muted">
            Colour
          </legend>
          <p className="mt-1 text-sm text-ink" id={colorLabelId}>
            {selectedColor ?? "Select a colour"}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {colors.map((color) => {
              const available = isColorAvailable(color)
              const selected = selectedColor === color
              return (
                <button
                  key={color}
                  type="button"
                  aria-pressed={selected}
                  disabled={!available}
                  onClick={() => setSelectedColor(color)}
                  className={optionClass(selected, !available)}
                >
                  {color}
                </button>
              )
            })}
          </div>
        </fieldset>
      ) : null}

      {sizeRequired ? (
        <fieldset>
          <legend className="text-xs font-medium uppercase tracking-label text-muted">
            Size
          </legend>
          <p className="mt-1 text-sm text-ink" id={sizeLabelId}>
            {selectedSize ?? "Select a size"}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {sizes.map((size) => {
              const available = isSizeAvailable(size)
              const selected = selectedSize === size
              return (
                <button
                  key={size}
                  type="button"
                  aria-pressed={selected}
                  disabled={!available}
                  onClick={() => setSelectedSize(size)}
                  className={optionClass(selected, !available)}
                >
                  {size}
                </button>
              )
            })}
          </div>
        </fieldset>
      ) : null}

      <div className="border-t border-line pt-5">
        {selectedVariant ? (
          <div className="space-y-2">
            <PriceDisplay
              amountMinor={selectedVariant.priceMinor}
              compareAtMinor={selectedVariant.compareAtPriceMinor ?? undefined}
              currency="VND"
              size="lg"
            />
            <StockIndicator
              inStock={selectedVariant.inStock}
              quantity={selectedVariant.stockQuantity}
            />
            <p className="text-xs text-muted">SKU: {selectedVariant.sku}</p>
          </div>
        ) : (
          <p className="text-sm text-muted">
            Select your colour and size to see availability.
          </p>
        )}
      </div>

      <p role="status" aria-live="polite" className="sr-only">
        {selectionMessage}
      </p>
    </div>
  )
}

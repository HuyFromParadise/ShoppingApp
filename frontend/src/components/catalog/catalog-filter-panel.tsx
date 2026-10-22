"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { catalogHref } from "@/features/catalog/query"
import type { Category } from "@/types/catalog"
import type { CatalogFilters } from "@/lib/validation/catalog"
import { cn } from "@/lib/utils/cn"

export interface CatalogFilterPanelProps {
  basePath: string
  filters: CatalogFilters
  categories: Category[]
  lockedCategory?: string
  onApplied?: () => void
  className?: string
  idPrefix?: string
}

function parseMoney(value: string): number | undefined {
  if (!value.trim()) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined
}

export function CatalogFilterPanel({
  basePath,
  filters,
  categories,
  lockedCategory,
  onApplied,
  className,
  idPrefix = "catalog",
}: CatalogFilterPanelProps) {
  const router = useRouter()
  const [category, setCategory] = React.useState(
    lockedCategory ?? filters.category ?? "",
  )
  const [variantSize, setVariantSize] = React.useState(filters.variantSize ?? "")
  const [color, setColor] = React.useState(filters.color ?? "")
  const [minPrice, setMinPrice] = React.useState(
    filters.minPrice !== undefined ? String(filters.minPrice) : "",
  )
  const [maxPrice, setMaxPrice] = React.useState(
    filters.maxPrice !== undefined ? String(filters.maxPrice) : "",
  )
  const [inStock, setInStock] = React.useState(filters.inStock === true)
  const [onSale, setOnSale] = React.useState(filters.onSale === true)

  React.useEffect(() => {
    setCategory(lockedCategory ?? filters.category ?? "")
    setVariantSize(filters.variantSize ?? "")
    setColor(filters.color ?? "")
    setMinPrice(filters.minPrice !== undefined ? String(filters.minPrice) : "")
    setMaxPrice(filters.maxPrice !== undefined ? String(filters.maxPrice) : "")
    setInStock(filters.inStock === true)
    setOnSale(filters.onSale === true)
  }, [filters, lockedCategory])

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next: CatalogFilters = {
      ...filters,
      category: lockedCategory ?? (category.trim() || undefined),
      variantSize: variantSize.trim() || undefined,
      color: color.trim() || undefined,
      minPrice: parseMoney(minPrice),
      maxPrice: parseMoney(maxPrice),
      inStock: inStock ? true : undefined,
      onSale: onSale ? true : undefined,
      page: 0,
    }
    router.push(catalogHref(basePath, next))
    onApplied?.()
  }

  function clear() {
    router.push(basePath)
    onApplied?.()
  }

  const id = (name: string) => `${idPrefix}-${name}`

  return (
    <div className={cn("space-y-5", className)}>
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-sm font-medium text-ink">Filters</h2>
        <button
          type="button"
          onClick={clear}
          className="text-xs text-muted underline underline-offset-4 transition-colors hover:text-ink"
        >
          Clear all
        </button>
      </div>

      <form onSubmit={submit} className="space-y-5">
        {!lockedCategory ? (
          <div className="space-y-1.5">
            <label htmlFor={id("category")} className="block text-sm font-medium text-ink">
              Category
            </label>
            <select
              id={id("category")}
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              className="h-10 w-full rounded border border-line bg-surface px-3 text-sm text-ink focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
            >
              <option value="">All categories</option>
              {categories.map((item) => (
                <option key={item.id} value={item.slug}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>
        ) : null}

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label htmlFor={id("size")} className="block text-sm font-medium text-ink">
              Size
            </label>
            <Input
              id={id("size")}
              name="variantSize"
              value={variantSize}
              onChange={(event) => setVariantSize(event.target.value)}
              placeholder="M"
              autoComplete="off"
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor={id("color")} className="block text-sm font-medium text-ink">
              Colour
            </label>
            <Input
              id={id("color")}
              name="color"
              value={color}
              onChange={(event) => setColor(event.target.value)}
              placeholder="Black"
              autoComplete="off"
            />
          </div>
        </div>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-ink">Price (VND)</legend>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor={id("min-price")} className="sr-only">
                Minimum price
              </label>
              <Input
                id={id("min-price")}
                name="minPrice"
                type="number"
                min={0}
                step={1000}
                inputMode="numeric"
                value={minPrice}
                onChange={(event) => setMinPrice(event.target.value)}
                placeholder="Min"
              />
            </div>
            <div>
              <label htmlFor={id("max-price")} className="sr-only">
                Maximum price
              </label>
              <Input
                id={id("max-price")}
                name="maxPrice"
                type="number"
                min={0}
                step={1000}
                inputMode="numeric"
                value={maxPrice}
                onChange={(event) => setMaxPrice(event.target.value)}
                placeholder="Max"
              />
            </div>
          </div>
        </fieldset>

        <fieldset className="space-y-3">
          <legend className="text-sm font-medium text-ink">Availability</legend>
          <label htmlFor={id("in-stock")} className="flex cursor-pointer items-center gap-2 text-sm text-ink">
            <input
              id={id("in-stock")}
              type="checkbox"
              checked={inStock}
              onChange={(event) => setInStock(event.target.checked)}
              className="h-4 w-4 rounded border-line accent-ink"
            />
            In stock only
          </label>
          <label htmlFor={id("on-sale")} className="flex cursor-pointer items-center gap-2 text-sm text-ink">
            <input
              id={id("on-sale")}
              type="checkbox"
              checked={onSale}
              onChange={(event) => setOnSale(event.target.checked)}
              className="h-4 w-4 rounded border-line accent-ink"
            />
            On sale
          </label>
        </fieldset>

        <Button type="submit" className="w-full">
          Apply filters
        </Button>
      </form>
    </div>
  )
}

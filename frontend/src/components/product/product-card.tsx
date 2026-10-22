import Link from "next/link"
import { PriceDisplay } from "@/components/ui/price-display"
import { cn } from "@/lib/utils/cn"
import type { ProductSummary } from "@/types/catalog"
import { ProductImage } from "./product-image"
import { SaleBadge } from "./sale-badge"

export interface ProductCardProps {
  product: ProductSummary
  className?: string
}

export function ProductCard({ product, className }: ProductCardProps) {
  const imageAlt =
    product.primaryImage?.altText?.trim() || product.name

  return (
    <article className={cn("group", className)}>
      <Link
        href={`/products/${encodeURIComponent(product.slug)}`}
        className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-4 focus-visible:ring-offset-paper"
      >
        <div className="relative aspect-[4/5] overflow-hidden border border-line bg-surface">
          <ProductImage
            src={product.primaryImage?.url}
            alt={imageAlt}
            className="transition-transform duration-150 group-hover:scale-[1.015]"
            imgClassName={!product.inStock ? "opacity-70" : undefined}
            sizes="(min-width: 1024px) 25vw, (min-width: 640px) 50vw, 100vw"
          />
          <div className="absolute left-3 top-3 flex flex-wrap gap-2">
            {product.onSale ? <SaleBadge /> : null}
            {!product.inStock ? (
              <span className="inline-flex items-center rounded-sm border border-line bg-surface/95 px-2 py-0.5 text-xs font-medium uppercase tracking-label text-muted">
                Out of stock
              </span>
            ) : null}
          </div>
        </div>

        <div className="mt-4 space-y-1.5">
          <p className="text-[11px] font-medium uppercase tracking-label text-muted">
            {product.category.name}
          </p>
          <h3 className="text-sm font-medium tracking-tight text-ink">
            {product.name}
          </h3>
          <PriceDisplay
            amountMinor={product.priceMinor}
            compareAtMinor={product.compareAtPriceMinor ?? undefined}
            currency={product.currency}
            size="sm"
          />
        </div>
      </Link>
    </article>
  )
}

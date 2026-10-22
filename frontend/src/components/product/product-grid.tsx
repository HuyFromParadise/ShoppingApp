import type * as React from "react"
import { EmptyState } from "@/components/ui/empty-state"
import { cn } from "@/lib/utils/cn"
import type { ProductSummary } from "@/types/catalog"
import { ProductCard } from "./product-card"

export interface ProductGridProps {
  products: ProductSummary[]
  className?: string
  emptyTitle?: string
  emptyDescription?: string
  emptyAction?: React.ReactNode
}

export function ProductGrid({
  products,
  className,
  emptyTitle = "No products found",
  emptyDescription = "Try removing a filter or searching for something else.",
  emptyAction,
}: ProductGridProps) {
  if (products.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        action={emptyAction}
      />
    )
  }

  return (
    <ul
      className={cn(
        "grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 lg:grid-cols-3",
        className,
      )}
    >
      {products.map((product) => (
        <li key={product.id}>
          <ProductCard product={product} />
        </li>
      ))}
    </ul>
  )
}

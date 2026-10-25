"use client"

import * as React from "react"
import Link from "next/link"
import { getProduct } from "@/features/admin/api"
import { DataTable, type Column } from "@/components/admin/data-table"
import { StockEditor } from "@/components/admin/stock-editor"
import { Badge } from "@/components/ui/badge"
import { ErrorState } from "@/components/ui/error-state"
import { Input } from "@/components/ui/input"
import { useAdminProducts } from "@/features/admin/hooks"
import { cn } from "@/lib/utils/cn"
import type { AdminProductSummary, AdminVariant } from "@/types/admin"

const PAGE_SIZE = 50
const LOW_STOCK = 5

interface InventoryRow extends AdminVariant {
  productId: string
  productName: string
  productStatus: string
}

/**
 * Inventory (PROJECT_SPEC §6: `/admin/inventory`). Stock is per variant, so the
 * table lists variants. It loads product pages and flattens their variants;
 * the dashboard already reports the low-stock subset, this is the full working
 * list an operator uses to restock.
 */
export default function AdminInventoryPage() {
  const products = useAdminProducts({ page: 0, size: PAGE_SIZE })
  const [search, setSearch] = React.useState("")
  const [onlyLow, setOnlyLow] = React.useState(false)

  // Variants are not a standalone endpoint, so each product is read for its
  // variants. The list page is bounded and the catalogue is small at MVP scale.
  const details = useProductVariants(products.data?.items ?? [])

  if (products.error) {
    return (
      <ErrorState
        title="We could not load inventory"
        onRetry={() => void products.refetch()}
      />
    )
  }

  const needle = search.trim().toLowerCase()
  const rows = details.rows
    .filter(
      (row) =>
        needle.length === 0 ||
        row.productName.toLowerCase().includes(needle) ||
        row.sku.toLowerCase().includes(needle) ||
        (row.color ?? "").toLowerCase().includes(needle) ||
        (row.size ?? "").toLowerCase().includes(needle),
    )
    .filter((row) => !onlyLow || row.stockQuantity <= LOW_STOCK)
    .sort((a, b) => a.stockQuantity - b.stockQuantity)

  const lowCount = details.rows.filter((row) => row.stockQuantity <= LOW_STOCK).length
  const outCount = details.rows.filter((row) => row.stockQuantity === 0).length

  const columns: ReadonlyArray<Column<InventoryRow>> = [
    {
      key: "product",
      header: "Product",
      render: (row) => (
        <div>
          <Link
            href={`/admin/products/${row.productId}`}
            className="font-medium text-ink underline-offset-4 hover:underline"
          >
            {row.productName}
          </Link>
          <p className="text-xs text-muted">
            {[row.color, row.size].filter(Boolean).join(" / ") || "Default"}
          </p>
        </div>
      ),
    },
    {
      key: "sku",
      header: "SKU",
      hideOnMobile: true,
      render: (row) => <span className="text-xs text-muted">{row.sku}</span>,
    },
    {
      key: "status",
      header: "Variant",
      hideOnMobile: true,
      render: (row) =>
        row.active ? (
          <span className="text-xs text-muted">Active</span>
        ) : (
          <Badge variant="neutral">Inactive</Badge>
        ),
    },
    {
      key: "stock",
      header: "Stock",
      align: "right",
      render: (row) => (
        <div className="flex justify-end">
          <StockEditor
            variantId={row.id}
            productId={row.productId}
            current={row.stockQuantity}
            threshold={LOW_STOCK}
          />
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Inventory</h1>
          <p className="mt-1 text-xs text-muted">
            Stock is set as an absolute total per variant. Every change is recorded
            with who made it and why.
          </p>
        </div>
        <dl className="flex gap-6 text-sm">
          <div>
            <dt className="text-xs uppercase tracking-label text-muted">Low</dt>
            <dd className="tabular-nums text-ink">{lowCount}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-label text-muted">Out</dt>
            <dd className={cn("tabular-nums", outCount > 0 ? "text-accent" : "text-ink")}>
              {outCount}
            </dd>
          </div>
        </dl>
      </header>

      <div className="flex flex-wrap items-end gap-4">
        <label className="min-w-56 flex-1 space-y-1">
          <span className="block text-xs text-muted">Filter variants</span>
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Product, SKU, colour or size"
            maxLength={100}
          />
        </label>
        <label className="flex items-center gap-2 pb-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={onlyLow}
            onChange={(event) => setOnlyLow(event.target.checked)}
            className="h-4 w-4 accent-[var(--color-ink)]"
          />
          Only low stock ({LOW_STOCK} or fewer)
        </label>
      </div>

      <DataTable
        caption="Variant stock"
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        loading={products.isPending || details.loading}
        emptyMessage="No variants match this filter."
      />

      {details.error ? (
        <p role="alert" className="text-xs text-accent">
          Some products could not be loaded, so their variants are missing from
          this list. Reload to try again.
        </p>
      ) : null}
    </div>
  )
}

/**
 * Loads variants for each product on the current page. One request per product,
 * issued in parallel; the admin inventory screen is not a hot path and there is
 * no variant-listing endpoint to call instead.
 */
function useProductVariants(products: readonly AdminProductSummary[]) {
  const [byProduct, setByProduct] = React.useState<Record<string, AdminVariant[]>>({})
  const [loading, setLoading] = React.useState(products.length > 0)
  const [error, setError] = React.useState(false)

  React.useEffect(() => {
    let cancelled = false
    if (products.length === 0) {
      setLoading(false)
      return () => {
        cancelled = true
      }
    }
    setLoading(true)
    setError(false)
    void (async () => {
      try {
        const loaded = await Promise.all(
          products.map(async (product) => {
            const detail = await getProduct(product.id)
            return [product.id, detail.variants] as const
          }),
        )
        if (cancelled) return
        setByProduct(Object.fromEntries(loaded))
      } catch {
        if (!cancelled) setError(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [products])

  const rows = React.useMemo<InventoryRow[]>(
    () =>
      products.flatMap((product) =>
        (byProduct[product.id] ?? []).map((variant) => ({
          ...variant,
          productId: product.id,
          productName: product.name,
          productStatus: product.status,
        })),
      ),
    [byProduct, products],
  )

  return { rows, loading, error }
}

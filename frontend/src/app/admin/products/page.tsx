"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { DataTable, type Column } from "@/components/admin/data-table"
import { SelectField } from "@/components/admin/form-controls"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { Input } from "@/components/ui/input"
import { PriceDisplay } from "@/components/ui/price-display"
import { useAdminCategories, useAdminProducts } from "@/features/admin/hooks"
import {
  PRODUCT_STATUSES,
  productStatusLabel,
  type AdminProductSummary,
} from "@/types/admin"

const PAGE_SIZE = 20

/**
 * Product list (PROJECT_SPEC §6: search and filter products). Filters live in
 * the URL so a filtered view can be bookmarked and the back button works.
 */
export default function AdminProductsPage() {
  return (
    <React.Suspense fallback={null}>
      <ProductsView />
    </React.Suspense>
  )
}

function ProductsView() {
  const params = useSearchParams()
  const router = useRouter()

  const q = params.get("q") ?? ""
  const status = params.get("status") ?? ""
  const categoryId = params.get("categoryId") ?? ""
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0)

  const [search, setSearch] = React.useState(q)
  const products = useAdminProducts({
    q: q || undefined,
    status: status || undefined,
    categoryId: categoryId || undefined,
    page,
    size: PAGE_SIZE,
  })
  const categories = useAdminCategories()

  React.useEffect(() => setSearch(q), [q])

  function apply(next: Partial<Record<"q" | "status" | "categoryId", string>>) {
    const nextParams = new URLSearchParams()
    const merged = { q, status, categoryId, ...next }
    if (merged.q) nextParams.set("q", merged.q)
    if (merged.status) nextParams.set("status", merged.status)
    if (merged.categoryId) nextParams.set("categoryId", merged.categoryId)
    // Any filter change resets paging, so a page cannot point past the result.
    const query = nextParams.toString()
    router.push(query ? `/admin/products?${query}` : "/admin/products")
  }

  const columns: ReadonlyArray<Column<AdminProductSummary>> = [
    {
      key: "product",
      header: "Product",
      render: (row) => (
        <div className="flex items-center gap-3">
          <div className="h-14 w-11 shrink-0 overflow-hidden border border-line bg-[#f5f3f0]">
            {row.primaryImageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={row.primaryImageUrl}
                alt=""
                className="h-full w-full object-cover"
              />
            ) : null}
          </div>
          <div className="min-w-0">
            <Link
              href={`/admin/products/${row.id}`}
              className="font-medium text-ink underline-offset-4 hover:underline"
            >
              {row.name}
            </Link>
            <p className="text-xs text-muted">{row.slug}</p>
          </div>
        </div>
      ),
    },
    {
      key: "category",
      header: "Category",
      hideOnMobile: true,
      render: (row) => <span className="text-muted">{row.categoryName ?? "—"}</span>,
    },
    {
      key: "price",
      header: "Price",
      align: "right",
      render: (row) => (
        <PriceDisplay amountMinor={row.basePriceMinor} currency={row.currency} size="sm" />
      ),
    },
    {
      key: "stock",
      header: "Stock",
      align: "right",
      render: (row) => (
        <span className={row.totalStock === 0 ? "text-accent" : undefined}>
          {row.totalStock}
        </span>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => (
        <Badge
          variant={
            row.status === "ACTIVE"
              ? "success"
              : row.status === "ARCHIVED"
                ? "neutral"
                : "warning"
          }
        >
          {productStatusLabel(row.status)}
        </Badge>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Products</h1>
          <p className="mt-1 text-xs text-muted">
            Products are archived, never deleted, so order history stays intact.
          </p>
        </div>
        <Link href="/admin/products/new" className={buttonVariants()}>
          New product
        </Link>
      </header>

      <form
        role="search"
        className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_12rem_14rem_auto]"
        onSubmit={(event) => {
          event.preventDefault()
          apply({ q: search.trim() })
        }}
      >
        <div>
          <label htmlFor="product-search" className="mb-1.5 block text-sm font-medium text-ink">
            Search
          </label>
          <Input
            id="product-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Name or slug"
            maxLength={100}
          />
        </div>
        <SelectField
          id="product-status"
          label="Status"
          value={status}
          placeholder="All statuses"
          onChange={(event) => apply({ status: event.target.value })}
          options={PRODUCT_STATUSES.map((value) => ({
            value,
            label: productStatusLabel(value),
          }))}
        />
        <SelectField
          id="product-category"
          label="Category"
          value={categoryId}
          placeholder="All categories"
          onChange={(event) => apply({ categoryId: event.target.value })}
          options={(categories.data ?? []).map((category) => ({
            value: category.id,
            label: category.name,
          }))}
        />
        <div className="flex items-end">
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </div>
      </form>

      {products.error ? (
        <ErrorState
          title="We could not load the products"
          onRetry={() => void products.refetch()}
        />
      ) : products.data && products.data.items.length === 0 && !q && !status && !categoryId ? (
        <EmptyState
          title="No products yet"
          description="Create your first product to start selling."
          action={
            <Link href="/admin/products/new" className={buttonVariants()}>
              New product
            </Link>
          }
        />
      ) : (
        <DataTable
          caption="Products"
          columns={columns}
          rows={products.data?.items ?? []}
          rowKey={(row) => row.id}
          loading={products.isPending}
          emptyMessage="No products match these filters."
        />
      )}

      {(products.data?.totalPages ?? 0) > 1 ? (
        <nav aria-label="Product pages" className="flex items-center justify-between gap-4">
          <Button
            variant="secondary"
            size="sm"
            disabled={page === 0}
            onClick={() => {
              const search = new URLSearchParams(params.toString())
              search.set("page", String(page - 1))
              router.push(`/admin/products?${search.toString()}`)
            }}
          >
            Previous
          </Button>
          <p className="text-xs text-muted">
            Page {page + 1} of {products.data?.totalPages}
          </p>
          <Button
            variant="secondary"
            size="sm"
            disabled={page + 1 >= (products.data?.totalPages ?? 1)}
            onClick={() => {
              const search = new URLSearchParams(params.toString())
              search.set("page", String(page + 1))
              router.push(`/admin/products?${search.toString()}`)
            }}
          >
            Next
          </Button>
        </nav>
      ) : null}
    </div>
  )
}

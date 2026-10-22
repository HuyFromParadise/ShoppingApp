import type { Metadata } from "next"
import Link from "next/link"
import { notFound } from "next/navigation"
import { Breadcrumbs } from "@/components/layout/breadcrumbs"
import { CatalogResults } from "@/components/catalog/catalog-results"
import { ErrorState } from "@/components/ui/error-state"
import { buttonVariants } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import {
  listCategories,
  listCategoryProducts,
} from "@/lib/api/endpoints/catalog"
import { safeParseCatalogFilters } from "@/lib/validation/catalog"
import type { Category, ProductSummary } from "@/types/catalog"
import type { Paginated } from "@/lib/api/types"

interface CategoryPageProps {
  params: Promise<{ slug: string }>
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function titleFromSlug(slug: string): string {
  return slug
    .split("-")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ")
}

export async function generateMetadata({
  params,
}: Pick<CategoryPageProps, "params">): Promise<Metadata> {
  const { slug } = await params
  return {
    title: titleFromSlug(slug),
    description: `Shop ${titleFromSlug(slug).toLowerCase()} at Clothing.`,
  }
}

export default async function CategoryPage({
  params,
  searchParams,
}: CategoryPageProps) {
  const { slug } = await params
  const parsed = safeParseCatalogFilters(await searchParams)
  const basePath = `/categories/${encodeURIComponent(slug)}`

  if (!parsed.success) {
    return (
      <div className="container-store py-12 sm:py-16">
        <Breadcrumbs
          items={[
            { label: "Home", href: "/" },
            { label: "Shop", href: "/shop" },
            { label: titleFromSlug(slug) },
          ]}
        />
        <div className="mt-8">
          <ErrorState
            title="Invalid catalog filters"
            description={
              parsed.error.issues[0]?.message ??
              "Check the filters in the address bar and try again."
            }
            action={
              <Link href={basePath} className={buttonVariants({ variant: "secondary" })}>
                Reset filters
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  const filters = parsed.data
  const [productsResult, categoriesResult] = await Promise.allSettled([
    listCategoryProducts(slug, filters),
    listCategories(),
  ])

  if (productsResult.status === "rejected") {
    if (
      productsResult.reason instanceof ApiError &&
      productsResult.reason.status === 404
    ) {
      notFound()
    }

    return (
      <div className="container-store py-12 sm:py-16">
        <Breadcrumbs
          items={[
            { label: "Home", href: "/" },
            { label: "Shop", href: "/shop" },
            { label: titleFromSlug(slug) },
          ]}
        />
        <div className="mt-8">
          <ErrorState
            title="Category unavailable"
            description={
              productsResult.reason instanceof ApiError
                ? productsResult.reason.message
                : "We could not load this category. Please try again shortly."
            }
            action={
              <Link href={basePath} className={buttonVariants({ variant: "secondary" })}>
                Try again
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  const page: Paginated<ProductSummary> = productsResult.value
  const categories: Category[] =
    categoriesResult.status === "fulfilled" ? categoriesResult.value : []
  const categoryRecord = categories.find((item) => item.slug === slug)
  const category = categoryRecord ?? page.items[0]?.category
  const categoryName = category?.name ?? titleFromSlug(slug)
  const description = categoryRecord?.description

  return (
    <div className="container-store py-10 sm:py-14">
      <Breadcrumbs
        items={[
          { label: "Home", href: "/" },
          { label: "Shop", href: "/shop" },
          { label: categoryName },
        ]}
      />

      <div className="mt-8 border-b border-line pb-8">
        <p className="text-xs font-medium uppercase tracking-label text-muted">
          Category
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-display text-ink sm:text-4xl">
          {filters.q ? `Search in ${categoryName}` : categoryName}
        </h1>
        <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted">
          {description?.trim() ||
            `Explore ${categoryName.toLowerCase()} in the current collection.`}
        </p>
      </div>

      <div className="mt-10">
        <CatalogResults
          basePath={basePath}
          filters={filters}
          page={page}
          categories={categories}
          lockedCategory={slug}
        />
      </div>
    </div>
  )
}

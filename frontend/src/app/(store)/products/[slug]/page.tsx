import type { Metadata } from "next"
import Link from "next/link"
import { notFound } from "next/navigation"
import { Breadcrumbs } from "@/components/layout/breadcrumbs"
import { ProductGallery } from "@/components/product/product-gallery"
import { SaleBadge } from "@/components/product/sale-badge"
import { StockIndicator } from "@/components/product/stock-indicator"
import { VariantSelector } from "@/components/product/variant-selector"
import { PriceDisplay } from "@/components/ui/price-display"
import { ApiError } from "@/lib/api/client"
import { getProduct } from "@/lib/api/endpoints/catalog"
import type { ProductDetail } from "@/types/catalog"

interface ProductPageProps {
  params: Promise<{ slug: string }>
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
}: ProductPageProps): Promise<Metadata> {
  const { slug } = await params
  try {
    const product = await getProduct(slug)
    return {
      title: product.name,
      description:
        product.shortDescription?.trim() ||
        `Shop ${product.name} at Clothing.`,
    }
  } catch {
    return { title: titleFromSlug(slug) }
  }
}

export default async function ProductPage({ params }: ProductPageProps) {
  const { slug } = await params

  let product: ProductDetail
  try {
    product = await getProduct(slug)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound()
    }
    throw error
  }

  return (
    <div className="container-store py-10 sm:py-14">
      <Breadcrumbs
        items={[
          { label: "Home", href: "/" },
          { label: "Shop", href: "/shop" },
          {
            label: product.category.name,
            href: `/categories/${encodeURIComponent(product.category.slug)}`,
          },
          { label: product.name },
        ]}
      />

      <div className="mt-8 grid gap-10 lg:grid-cols-2 lg:gap-16">
        <ProductGallery images={product.images} productName={product.name} />

        <div className="lg:sticky lg:top-24 lg:self-start">
          <div className="flex flex-wrap items-center gap-3">
            <Link
              href={`/categories/${encodeURIComponent(product.category.slug)}`}
              className="text-xs font-medium uppercase tracking-label text-muted transition-colors hover:text-ink"
            >
              {product.category.name}
            </Link>
            {product.onSale ? <SaleBadge /> : null}
          </div>

          <h1 className="mt-4 text-3xl font-semibold tracking-display text-ink sm:text-4xl">
            {product.name}
          </h1>

          {product.shortDescription ? (
            <p className="mt-3 max-w-lg text-base leading-relaxed text-muted">
              {product.shortDescription}
            </p>
          ) : null}

          <div className="mt-6">
            <PriceDisplay
              amountMinor={product.priceMinor}
              compareAtMinor={product.compareAtPriceMinor ?? undefined}
              currency={product.currency}
              size="lg"
            />
          </div>

          <div className="mt-3">
            <StockIndicator inStock={product.inStock} />
          </div>

          <div className="mt-8 border-t border-line pt-8">
            <VariantSelector variants={product.variants} />
          </div>

          {product.description ? (
            <section className="mt-10 border-t border-line pt-8">
              <h2 className="text-sm font-medium text-ink">Description</h2>
              <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-muted">
                {product.description}
              </p>
            </section>
          ) : null}

          {product.material || product.fit || product.careInstructions ? (
            <section className="mt-8 border-t border-line pt-8">
              <h2 className="text-sm font-medium text-ink">Details</h2>
              <dl className="mt-3 divide-y divide-line text-sm">
                {product.material ? (
                  <div className="grid grid-cols-[7rem_1fr] gap-4 py-3">
                    <dt className="text-muted">Material</dt>
                    <dd className="text-ink">{product.material}</dd>
                  </div>
                ) : null}
                {product.fit ? (
                  <div className="grid grid-cols-[7rem_1fr] gap-4 py-3">
                    <dt className="text-muted">Fit</dt>
                    <dd className="text-ink">{product.fit}</dd>
                  </div>
                ) : null}
                {product.careInstructions ? (
                  <div className="grid grid-cols-[7rem_1fr] gap-4 py-3">
                    <dt className="text-muted">Care</dt>
                    <dd className="text-ink">{product.careInstructions}</dd>
                  </div>
                ) : null}
              </dl>
            </section>
          ) : null}
        </div>
      </div>
    </div>
  )
}

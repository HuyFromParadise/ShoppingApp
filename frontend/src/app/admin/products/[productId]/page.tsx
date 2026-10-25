"use client"

import * as React from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { ProductImages } from "@/components/admin/product-images"
import { ProductForm, productToForm, validateProductForm, type ProductFormErrors, type ProductFormValues } from "@/components/admin/product-form"
import { AddVariantForm, VariantList } from "@/components/admin/variant-editor"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { useAdminCategories, useAdminProduct, useUpdateProduct } from "@/features/admin/hooks"
import { ApiError } from "@/lib/api/client"
import { productStatusLabel, type UpdateProductInput } from "@/types/admin"

/**
 * Edit product (PROJECT_SPEC §6: `/admin/products/[productId]`). Saving patches
 * only what changed; the server audits the real changes rather than the request.
 */
export default function EditProductPage() {
  const params = useParams<{ productId: string }>()
  const productId = typeof params?.productId === "string" ? params.productId : undefined
  return <EditProductView productId={productId} />
}

function EditProductView({ productId }: { productId: string | undefined }) {
  const product = useAdminProduct(productId)
  const categories = useAdminCategories()

  if (!productId) {
    return <ErrorState title="We could not find that product" />
  }
  if (product.isPending || categories.isPending) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton lines={8} />
      </div>
    )
  }
  if (product.error) {
    return product.error instanceof ApiError && product.error.status === 404 ? (
      <ErrorState
        title="We could not find that product"
        description="It may have been archived or removed."
        action={
          <Link href="/admin/products" className={buttonVariants({ variant: "secondary", size: "sm" })}>
            Back to products
          </Link>
        }
      />
    ) : (
      <ErrorState title="We could not load the product" onRetry={() => void product.refetch()} />
    )
  }
  if (!product.data) {
    return <ErrorState title="We could not find that product" />
  }

  return (
    <ProductEditor
      productId={productId}
      product={product.data}
      categories={categories.data ?? []}
      onReload={() => void product.refetch()}
    />
  )
}

function ProductEditor({
  productId,
  product,
  categories,
  onReload,
}: {
  productId: string
  product: NonNullable<ReturnType<typeof useAdminProduct>["data"]>
  categories: NonNullable<ReturnType<typeof useAdminCategories>["data"]>
  onReload: () => void
}) {
  const update = useUpdateProduct(productId)
  const { toast } = useToast()
  const [values, setValues] = React.useState<ProductFormValues>(() => productToForm(product))
  const [errors, setErrors] = React.useState<ProductFormErrors>({})

  // A server re-read after a save is the new truth; the form follows it.
  React.useEffect(() => {
    setValues(productToForm(product))
  }, [product])

  function submit() {
    const found = validateProductForm(values, { requireCategory: true })
    if (Object.keys(found).length > 0) {
      setErrors(found)
      return
    }
    setErrors({})

    const input: UpdateProductInput = {
      name: values.name.trim(),
      categoryId: values.categoryId,
      shortDescription: values.shortDescription.trim(),
      description: values.description.trim(),
      material: values.material.trim(),
      fit: values.fit.trim(),
      careInstructions: values.careInstructions.trim(),
      featured: values.featured,
      status: values.status as UpdateProductInput["status"],
      ...(values.basePriceMinor === null ? {} : { basePrice: values.basePriceMinor }),
    }

    update.mutate(input, {
      onSuccess: () => {
        toast({ title: "Product saved", variant: "success" })
        onReload()
      },
      onError: (failure) => {
        if (failure instanceof ApiError) {
          const fieldErrors: ProductFormErrors = {}
          for (const [key, message] of Object.entries(failure.fieldErrors ?? {})) {
            fieldErrors[(key === "basePrice" ? "basePriceMinor" : key) as keyof ProductFormValues] =
              message
          }
          setErrors(fieldErrors)
        }
        toast({
          title: "We could not save the product",
          description: failure instanceof Error ? failure.message : "Try again.",
          variant: "error",
        })
      },
    })
  }

  const inactiveVariants = product.variants.filter((variant) => !variant.active).length

  return (
    <div className="space-y-10">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link
            href="/admin/products"
            className="text-xs text-muted underline underline-offset-4 hover:text-ink"
          >
            ← All products
          </Link>
          <h1 className="mt-3 text-xl font-semibold tracking-tight text-ink">
            {product.name}
          </h1>
          <p className="mt-1 text-xs text-muted">/products/{product.slug}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge
            variant={
              product.status === "ACTIVE"
                ? "success"
                : product.status === "ARCHIVED"
                  ? "neutral"
                  : "warning"
            }
          >
            {productStatusLabel(product.status)}
          </Badge>
        </div>
      </header>

      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="space-y-10">
          <ProductForm
            values={values}
            errors={errors}
            categories={categories}
            submitLabel="Save changes"
            pending={update.isPending}
            onChange={setValues}
            onSubmit={submit}
            secondaryAction={
              <Link
                href="/admin/products"
                className={buttonVariants({ variant: "secondary" })}
              >
                Back
              </Link>
            }
          />

          <section aria-labelledby="variants-heading" className="space-y-4">
            <div className="flex items-baseline justify-between gap-3">
              <h2
                id="variants-heading"
                className="text-xs font-medium uppercase tracking-label text-muted"
              >
                Variants
              </h2>
              <p className="text-xs text-muted">
                {product.variants.length} total
                {inactiveVariants > 0 ? `, ${inactiveVariants} inactive` : ""}
              </p>
            </div>
            <VariantList
              productId={productId}
              variants={product.variants}
              currency={product.currency}
            />
            <AddVariantForm productId={productId} />
            <p className="text-xs text-muted">
              Stock is set as an absolute total and every change is recorded with
              who made it and why.
            </p>
          </section>
        </div>

        <aside>
          <ProductImages productId={productId} images={product.images} />
        </aside>
      </div>
    </div>
  )
}

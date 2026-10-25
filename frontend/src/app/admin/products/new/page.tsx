"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { ProductForm, emptyProductForm, validateProductForm, type ProductFormValues } from "@/components/admin/product-form"
import type { NewVariantDraft } from "@/components/admin/variant-editor"
import { buttonVariants } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { useAdminCategories, useCreateProduct } from "@/features/admin/hooks"
import { ApiError } from "@/lib/api/client"

/**
 * New product (PROJECT_SPEC §6: `/admin/products/new`). One variant is required,
 * so the draft starts with a single row and the operator can add more after the
 * product exists (variants are added to an existing product by the API).
 */
export default function NewProductPage() {
  const categories = useAdminCategories()
  const create = useCreateProduct()
  const router = useRouter()
  const { toast } = useToast()

  const [values, setValues] = React.useState<ProductFormValues | null>(null)
  const [variant, setVariant] = React.useState<NewVariantDraft>({
    color: "",
    size: "",
    sku: "",
    stock: "0",
  })
  const [errors, setErrors] = React.useState<ReturnType<typeof validateProductForm>>({})
  const [variantError, setVariantError] = React.useState<string | null>(null)

  React.useEffect(() => {
    if (!values && categories.data) setValues(emptyProductForm(categories.data))
  }, [categories.data, values])

  if (categories.error) {
    return (
      <ErrorState
        title="We could not load the categories"
        description="A product needs a category, so this form cannot open yet."
        onRetry={() => void categories.refetch()}
      />
    )
  }

  if (!values || categories.isPending) {
    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold tracking-tight text-ink">New product</h1>
        <Skeleton lines={8} />
      </div>
    )
  }

  if ((categories.data?.length ?? 0) === 0) {
    return (
      <ErrorState
        title="Create a category first"
        description="Every product belongs to a category."
        action={
          <Link href="/admin/categories" className={buttonVariants({ size: "sm" })}>
            Go to categories
          </Link>
        }
      />
    )
  }

  const stock = Number(variant.stock)
  const stockValid = variant.stock.trim() !== "" && Number.isInteger(stock) && stock >= 0

  function submit() {
    if (!values) return
    const found = validateProductForm(values, { requireCategory: true })
    if (!stockValid) {
      setVariantError("Opening stock must be a whole number, zero or more.")
    }
    if (Object.keys(found).length > 0 || !stockValid) {
      setErrors(found)
      return
    }
    setErrors({})
    setVariantError(null)
    create.mutate(
      {
        name: values.name.trim(),
        slug: values.slug.trim() || undefined,
        categoryId: values.categoryId,
        shortDescription: values.shortDescription.trim() || undefined,
        description: values.description.trim() || undefined,
        material: values.material.trim() || undefined,
        fit: values.fit.trim() || undefined,
        careInstructions: values.careInstructions.trim() || undefined,
        featured: values.featured,
        status: values.status,
        basePrice: values.basePriceMinor as number,
        variants: [
          {
            color: variant.color.trim() || undefined,
            size: variant.size.trim() || undefined,
            sku: variant.sku.trim() || undefined,
            stockQuantity: stock,
            active: true,
          },
        ],
      },
      {
        onSuccess: (product) => {
          toast({ title: "Product created", description: product.name, variant: "success" })
          router.push(`/admin/products/${product.id}`)
        },
        onError: (failure) => {
          if (failure instanceof ApiError) {
            const fieldErrors: Record<string, string> = {}
            for (const [key, message] of Object.entries(failure.fieldErrors ?? {})) {
              // Map server field names onto this form's fields.
              const target =
                key === "basePrice" ? "basePriceMinor" : key === "variants" ? "variants" : key
              fieldErrors[target] = message
            }
            setErrors(fieldErrors)
            if (fieldErrors.variants) setVariantError(fieldErrors.variants)
          }
          toast({
            title: "We could not create the product",
            description: failure instanceof Error ? failure.message : "Try again.",
            variant: "error",
          })
        },
      },
    )
  }

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-xl font-semibold tracking-tight text-ink">New product</h1>
        <p className="mt-1 text-xs text-muted">
          Saved as a draft unless you choose to publish it.
        </p>
      </header>

      <section aria-labelledby="variants-heading" className="space-y-3">
        <h2
          id="variants-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          First variant
        </h2>
        <div className="grid gap-3 border border-line bg-surface p-4 sm:grid-cols-4">
          <label className="space-y-1">
            <span className="block text-xs text-muted">Colour</span>
            <Input
              value={variant.color}
              onChange={(event) => setVariant({ ...variant, color: event.target.value })}
              maxLength={64}
              placeholder="Sand"
            />
          </label>
          <label className="space-y-1">
            <span className="block text-xs text-muted">Size</span>
            <Input
              value={variant.size}
              onChange={(event) => setVariant({ ...variant, size: event.target.value })}
              maxLength={32}
              placeholder="M"
            />
          </label>
          <label className="space-y-1">
            <span className="block text-xs text-muted">SKU (optional)</span>
            <Input
              value={variant.sku}
              onChange={(event) => setVariant({ ...variant, sku: event.target.value })}
              maxLength={64}
              placeholder="Generated if blank"
            />
          </label>
          <label className="space-y-1">
            <span className="block text-xs text-muted">Opening stock</span>
            <Input
              type="number"
              min={0}
              step={1}
              value={variant.stock}
              onChange={(event) => setVariant({ ...variant, stock: event.target.value })}
              aria-invalid={!stockValid || undefined}
            />
          </label>
        </div>
        {errors.variants || variantError ? (
          <p role="alert" className="text-xs text-accent">
            {errors.variants ?? variantError}
          </p>
        ) : null}
      </section>

      <ProductForm
        values={values}
        errors={errors}
        categories={categories.data ?? []}
        slugEditable
        submitLabel="Create product"
        pending={create.isPending}
        onChange={setValues}
        onSubmit={submit}
        secondaryAction={
          <Link
            href="/admin/products"
            className={buttonVariants({ variant: "secondary" })}
          >
            Cancel
          </Link>
        }
      />
    </div>
  )
}

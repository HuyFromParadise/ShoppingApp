"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { ProductForm, emptyProductForm, validateProductForm, type ProductFormValues } from "@/components/admin/product-form"
import {
  VariantDraftEditor,
  newVariantDraft,
  parseStock,
  validateVariantDrafts,
  type VariantDraft,
} from "@/components/admin/variant-drafts"
import { buttonVariants } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import { useAdminCategories, useCreateProduct } from "@/features/admin/hooks"
import { ApiError } from "@/lib/api/client"

const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"]

/**
 * New product (PROJECT_SPEC §6: `/admin/products/new`). One variant is required,
 * so the draft starts with a single row and the operator can add more after the
 * product exists (variants are added to an existing product by the API).
 *
 * The first image is chosen here and sent in the same request as the product,
 * because the upload endpoint needs a product id: without this the form could
 * only create a product with no photograph at all.
 */
export default function NewProductPage() {
  const categories = useAdminCategories()
  const create = useCreateProduct()
  const router = useRouter()
  const { toast } = useToast()

  const [values, setValues] = React.useState<ProductFormValues | null>(null)
  // A product needs at least one variant, so the form starts with one row.
  const [variants, setVariants] = React.useState<VariantDraft[]>([newVariantDraft()])
  const [variantErrors, setVariantErrors] = React.useState<Record<string, string>>({})
  const [errors, setErrors] = React.useState<ReturnType<typeof validateProductForm>>({})
  const [imageFile, setImageFile] = React.useState<File | null>(null)
  const [imageAlt, setImageAlt] = React.useState("")
  const [imageError, setImageError] = React.useState<string | null>(null)
  const previewUrl = useObjectUrl(imageFile)

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

  /** Browser-side check that mirrors the server's magic-byte rules. */
  function chooseImage(file: File | undefined) {
    setImageError(null)
    if (!file) {
      setImageFile(null)
      return
    }
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setImageError("Choose a JPEG, PNG or WebP image.")
      setImageFile(null)
      return
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setImageError("Images must be 5 MB or smaller.")
      setImageFile(null)
      return
    }
    setImageFile(file)
  }

  function submit() {
    if (!values) return
    const found = validateProductForm(values, { requireCategory: true })
    // Every row is checked, and the message lands on the row that caused it.
    const rowErrors = validateVariantDrafts(variants)
    if (Object.keys(found).length > 0 || Object.keys(rowErrors).length > 0 || imageError) {
      setErrors(found)
      setVariantErrors(rowErrors)
      return
    }
    setErrors({})
    setVariantErrors({})
    create.mutate(
      {
        input: {
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
          variants: variants.map((variant) => ({
            color: variant.color.trim() || undefined,
            size: variant.size.trim() || undefined,
            sku: variant.sku.trim() || undefined,
            // Validated above, so a null here cannot reach this point.
            stockQuantity: parseStock(variant.stock) as number,
            active: true,
          })),
        },
        image: imageFile,
        altText: imageAlt,
      },
      {
        onSuccess: (product) => {
          toast({
            title: "Product created",
            description: imageFile ? `${product.name} with its first image.` : product.name,
            variant: "success",
          })
          router.push(`/admin/products/${product.id}`)
        },
        onError: (failure) => {
          if (failure instanceof ApiError) {
            const fieldErrors: Record<string, string> = {}
            for (const [key, message] of Object.entries(failure.fieldErrors ?? {})) {
              // Map server field names onto this form's fields.
              if (key === "file") {
                // The image was refused, so no product exists either: report it
                // on the image field, which is what the operator must fix.
                setImageError(message)
                continue
              }
              const target =
                key === "basePrice" ? "basePriceMinor" : key === "variants" ? "variants" : key
              fieldErrors[target] = message
            }
            setErrors(fieldErrors)
            // A rejected combination is not tied to one row, so it is shown above
            // the list rather than guessed at a row.
            if (fieldErrors.variants) setVariantErrors({ __list: fieldErrors.variants })
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
          Variants
        </h2>
        <p className="text-xs text-muted">
          One row per size and colour. At least one is required; a product may
          have up to 100. Leave the SKU blank and it is generated.
        </p>
        {variantErrors.__list ? (
          <p role="alert" className="text-xs text-accent">
            {variantErrors.__list}
          </p>
        ) : null}
        <VariantDraftEditor
          drafts={variants}
          errors={variantErrors}
          disabled={create.isPending}
          onChange={setVariants}
        />
      </section>

      <section aria-labelledby="image-heading" className="space-y-3">
        <h2
          id="image-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          First image
        </h2>
        <div className="flex flex-wrap items-start gap-4 border border-line bg-surface p-4">
          <div className="h-28 w-21 aspect-[3/4] shrink-0 overflow-hidden border border-line bg-[#f5f3f0]">
            {previewUrl ? (
              /* eslint-disable-next-line @next/next/no-img-element */
              <img
                src={previewUrl}
                alt="Selected image preview"
                className="h-full w-full object-cover"
              />
            ) : (
              <p className="flex h-full items-center justify-center px-2 text-center text-[10px] uppercase tracking-label text-muted">
                No image
              </p>
            )}
          </div>
          <div className="min-w-56 flex-1 space-y-3">
            <label className="block space-y-1">
              <span className="block text-xs text-muted">
                Choose a file{" "}
                <span className="normal-case">(optional)</span>
              </span>
              <input
                type="file"
                accept={ACCEPTED_TYPES.join(",")}
                aria-label="Choose the first image"
                onChange={(event) => chooseImage(event.target.files?.[0])}
                className="text-sm text-muted file:mr-3 file:rounded file:border file:border-line file:bg-surface file:px-3 file:py-1.5 file:text-sm file:text-ink"
              />
            </label>
            <label className="block space-y-1">
              <span className="block text-xs text-muted">Alt text (optional)</span>
              <Input
                value={imageAlt}
                onChange={(event) => setImageAlt(event.target.value)}
                maxLength={200}
                placeholder="Describes the image for screen readers"
              />
            </label>
            {imageFile ? (
              <p className="text-xs text-muted">
                {imageFile.name} · sent with the product
              </p>
            ) : null}
            <p className="text-xs text-muted">
              JPEG, PNG or WebP, up to 5 MB. The image is checked on the server
              before the product is saved, so a bad file creates nothing.
            </p>
            {imageError ? (
              <p role="alert" className="text-xs text-accent">
                {imageError}
              </p>
            ) : null}
          </div>
        </div>
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

/**
 * An object URL for a local file preview, revoked when the file changes or the
 * component unmounts so a picked file is not kept alive in memory.
 */
function useObjectUrl(file: File | null): string | null {
  const [url, setUrl] = React.useState<string | null>(null)
  React.useEffect(() => {
    if (!file) {
      setUrl(null)
      return
    }
    const next = URL.createObjectURL(file)
    setUrl(next)
    return () => URL.revokeObjectURL(next)
  }, [file])
  return url
}
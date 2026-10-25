"use client"

import * as React from "react"
import { CheckboxField, MoneyField, SelectField, TextareaField } from "@/components/admin/form-controls"
import { Button } from "@/components/ui/button"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import { PRODUCT_STATUSES, productStatusLabel, type AdminCategory, type AdminProductDetail, type ProductStatus } from "@/types/admin"

export interface ProductFormValues {
  name: string
  slug: string
  categoryId: string
  shortDescription: string
  description: string
  material: string
  fit: string
  careInstructions: string
  featured: boolean
  status: ProductStatus
  basePriceMinor: number | null
}

export function emptyProductForm(categories: readonly AdminCategory[]): ProductFormValues {
  return {
    name: "",
    slug: "",
    categoryId: categories[0]?.id ?? "",
    shortDescription: "",
    description: "",
    material: "",
    fit: "",
    careInstructions: "",
    featured: false,
    status: "DRAFT",
    basePriceMinor: null,
  }
}

export function productToForm(product: AdminProductDetail): ProductFormValues {
  return {
    name: product.name,
    slug: product.slug,
    categoryId: product.categoryId,
    shortDescription: product.shortDescription ?? "",
    description: product.description ?? "",
    material: product.material ?? "",
    fit: product.fit ?? "",
    careInstructions: product.careInstructions ?? "",
    featured: product.featured,
    status: (PRODUCT_STATUSES.includes(product.status as ProductStatus)
      ? product.status
      : "DRAFT") as ProductStatus,
    basePriceMinor: product.basePriceMinor,
  }
}

export type ProductFormErrors = Partial<Record<keyof ProductFormValues | "variants", string>>

/**
 * Client-side checks that mirror the server's rules so the operator sees the
 * problem before a round trip. The server validates again; this is not a
 * substitute, only a faster path.
 */
export function validateProductForm(
  values: ProductFormValues,
  options: { requireCategory?: boolean } = {},
): ProductFormErrors {
  const errors: ProductFormErrors = {}
  if (!values.name.trim()) errors.name = "Name is required."
  else if (values.name.trim().length > 200) errors.name = "Must be at most 200 characters."
  if (options.requireCategory && !values.categoryId) {
    errors.categoryId = "Choose a category."
  }
  if (values.basePriceMinor === null || values.basePriceMinor <= 0) {
    errors.basePriceMinor = "Enter a price greater than zero."
  }
  if (values.shortDescription.length > 500) {
    errors.shortDescription = "Must be at most 500 characters."
  }
  if (values.slug && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) {
    errors.slug = "Lowercase letters, digits and single hyphens only."
  }
  return errors
}

export interface ProductFormProps {
  values: ProductFormValues
  errors: ProductFormErrors
  categories: readonly AdminCategory[]
  /** The slug is generated on create and immutable afterwards. */
  slugEditable?: boolean
  disabled?: boolean
  submitLabel: string
  pending?: boolean
  onChange: (next: ProductFormValues) => void
  onSubmit: () => void
  secondaryAction?: React.ReactNode
}

export function ProductForm({
  values,
  errors,
  categories,
  slugEditable = false,
  disabled = false,
  submitLabel,
  pending = false,
  onChange,
  onSubmit,
  secondaryAction,
}: ProductFormProps) {
  function set<K extends keyof ProductFormValues>(key: K, value: ProductFormValues[K]) {
    onChange({ ...values, [key]: value })
  }

  return (
    <form
      className="space-y-6"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit()
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="product-name" label="Name" required error={errors.name}>
          <Input
            value={values.name}
            onChange={(event) => set("name", event.target.value)}
            maxLength={200}
            disabled={disabled}
          />
        </FormField>
        <FormField
          id="product-slug"
          label="URL slug"
          error={errors.slug}
          hint={slugEditable ? "Leave blank to derive it from the name." : "Cannot be changed once the product exists."}
        >
          <Input
            value={values.slug}
            onChange={(event) => set("slug", event.target.value)}
            disabled={disabled || !slugEditable}
            maxLength={200}
          />
        </FormField>
        <SelectField
          id="product-category-field"
          label="Category"
          value={values.categoryId}
          error={errors.categoryId}
          onChange={(event) => set("categoryId", event.target.value)}
          placeholder="Choose a category"
          options={categories.map((category) => ({
            value: category.id,
            label: category.active ? category.name : `${category.name} (inactive)`,
          }))}
          disabled={disabled}
        />
        <MoneyField
          label="Base price"
          valueMinor={values.basePriceMinor}
          onChangeMinor={(value) => set("basePriceMinor", value)}
          error={errors.basePriceMinor}
          hint="Minor units. For VND the number is the price itself."
          disabled={disabled}
        />
        <SelectField
          id="product-status-field"
          label="Publication"
          value={values.status}
          onChange={(event) => set("status", event.target.value as ProductStatus)}
          options={PRODUCT_STATUSES.map((value) => ({
            value,
            label: productStatusLabel(value),
          }))}
          hint="Only live products appear in the storefront."
          disabled={disabled}
        />
        <div className="flex items-end pb-1">
          <CheckboxField
            label="Featured"
            hint="Shows in featured placements on the storefront."
            checked={values.featured}
            onChange={(event) => set("featured", event.target.checked)}
            disabled={disabled}
          />
        </div>
      </div>

      <FormField
        id="product-short-description"
        label="Short description"
        error={errors.shortDescription}
      >
        <Input
          value={values.shortDescription}
          onChange={(event) => set("shortDescription", event.target.value)}
          maxLength={500}
          disabled={disabled}
        />
      </FormField>

      <TextareaField
        label="Description"
        rows={5}
        value={values.description}
        onChange={(event) => set("description", event.target.value)}
        hint="Plain text."
        disabled={disabled}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="product-material" label="Material">
          <Input
            value={values.material}
            onChange={(event) => set("material", event.target.value)}
            maxLength={200}
            disabled={disabled}
          />
        </FormField>
        <FormField id="product-fit" label="Fit">
          <Input
            value={values.fit}
            onChange={(event) => set("fit", event.target.value)}
            maxLength={100}
            disabled={disabled}
          />
        </FormField>
      </div>

      <TextareaField
        label="Care instructions"
        rows={3}
        value={values.careInstructions}
        onChange={(event) => set("careInstructions", event.target.value)}
        disabled={disabled}
      />

      <div className="flex flex-wrap items-center gap-3 border-t border-line pt-5">
        <Button type="submit" loading={pending} disabled={disabled}>
          {submitLabel}
        </Button>
        {secondaryAction}
      </div>
    </form>
  )
}

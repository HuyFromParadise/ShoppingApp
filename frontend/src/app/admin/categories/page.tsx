"use client"

import * as React from "react"
import Link from "next/link"
import { DataTable, type Column } from "@/components/admin/data-table"
import { CheckboxField, SelectField, TextareaField } from "@/components/admin/form-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import { useToast } from "@/components/ui/toast"
import { useAdminCategories, useCreateCategory, useUpdateCategory } from "@/features/admin/hooks"
import { ApiError } from "@/lib/api/client"
import type { AdminCategory } from "@/types/admin"

/**
 * Categories (PROJECT_SPEC §6: `/admin/categories`). There is no delete: a
 * category is deactivated, which hides it from the storefront while keeping its
 * products and order history attached (ADR-009). The product count is shown so
 * that decision is informed.
 */
export default function AdminCategoriesPage() {
  const categories = useAdminCategories()
  const [creating, setCreating] = React.useState(false)

  const columns: ReadonlyArray<Column<AdminCategory>> = [
    {
      key: "name",
      header: "Category",
      render: (row) => (
        <div>
          <p className="font-medium text-ink">{row.name}</p>
          <p className="text-xs text-muted">/{row.slug}</p>
        </div>
      ),
    },
    {
      key: "parent",
      header: "Parent",
      hideOnMobile: true,
      render: (row) => {
        const parent = categories.data?.find((item) => item.id === row.parentId)
        return <span className="text-muted">{parent?.name ?? "—"}</span>
      },
    },
    {
      key: "products",
      header: "Products",
      align: "right",
      render: (row) => (
        <Link
          href={`/admin/products?categoryId=${encodeURIComponent(row.id)}`}
          className="tabular-nums text-ink underline-offset-4 hover:underline"
        >
          {row.productCount}
        </Link>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => (
        <Badge variant={row.active ? "success" : "neutral"}>
          {row.active ? "Visible" : "Hidden"}
        </Badge>
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (row) => <CategoryActions category={row} categories={categories.data ?? []} />,
    },
  ]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Categories</h1>
          <p className="mt-1 text-xs text-muted">
            Categories are hidden, never deleted, so product and order history stay intact.
          </p>
        </div>
        <Button onClick={() => setCreating((open) => !open)}>
          {creating ? "Close" : "New category"}
        </Button>
      </header>

      {creating ? (
        <CreateCategoryForm
          categories={categories.data ?? []}
          onDone={() => setCreating(false)}
        />
      ) : null}

      {categories.error ? (
        <ErrorState
          title="We could not load the categories"
          onRetry={() => void categories.refetch()}
        />
      ) : (
        <DataTable
          caption="Categories"
          columns={columns}
          rows={categories.data ?? []}
          rowKey={(row) => row.id}
          loading={categories.isPending}
          emptyMessage="No categories yet."
        />
      )}
    </div>
  )
}

function CreateCategoryForm({
  categories,
  onDone,
}: {
  categories: readonly AdminCategory[]
  onDone: () => void
}) {
  const create = useCreateCategory()
  const { toast } = useToast()
  const [name, setName] = React.useState("")
  const [slug, setSlug] = React.useState("")
  const [description, setDescription] = React.useState("")
  const [parentId, setParentId] = React.useState("")
  const [active, setActive] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setError("A name is required.")
      return
    }
    setError(null)
    create.mutate(
      {
        name: name.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
        parentId: parentId || undefined,
        active,
      },
      {
        onSuccess: (category) => {
          toast({ title: "Category created", description: category.name, variant: "success" })
          onDone()
        },
        onError: (failure) => {
          if (failure instanceof ApiError && failure.fieldErrors) {
            setError(Object.values(failure.fieldErrors)[0] ?? failure.message)
          } else {
            setError(failure instanceof Error ? failure.message : "Could not create the category.")
          }
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4 border border-line bg-surface p-5">
      <h2 className="text-xs font-medium uppercase tracking-label text-muted">New category</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="category-name" label="Name" required>
          <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={200} />
        </FormField>
        <FormField id="category-slug" label="Slug" hint="Leave blank to derive it from the name.">
          <Input value={slug} onChange={(event) => setSlug(event.target.value)} maxLength={200} />
        </FormField>
        <SelectField
          id="category-parent"
          label="Parent"
          value={parentId}
          placeholder="No parent"
          onChange={(event) => setParentId(event.target.value)}
          options={categories.map((category) => ({
            value: category.id,
            label: category.name,
          }))}
        />
        <div className="flex items-end pb-1">
          <CheckboxField
            label="Visible in the storefront"
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
        </div>
      </div>
      <TextareaField
        label="Description"
        rows={2}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
      />
      {error ? (
        <p role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
      <Button type="submit" loading={create.isPending}>
        Create category
      </Button>
    </form>
  )
}

function CategoryActions({
  category,
  categories,
}: {
  category: AdminCategory
  categories: readonly AdminCategory[]
}) {
  const update = useUpdateCategory()
  const { toast } = useToast()
  const [editing, setEditing] = React.useState(false)
  const [name, setName] = React.useState(category.name)
  const [sortOrder, setSortOrder] = React.useState(String(category.sortOrder))
  const [error, setError] = React.useState<string | null>(null)

  function save() {
    setError(null)
    const order = Number(sortOrder)
    if (sortOrder.trim() !== "" && (!Number.isInteger(order) || order < 0)) {
      setError("Sort order must be zero or more.")
      return
    }
    update.mutate(
      { categoryId: category.id, input: { name: name.trim(), sortOrder: order } },
      {
        onSuccess: () => {
          setEditing(false)
          toast({ title: "Category saved", variant: "success" })
        },
        onError: (failure) =>
          setError(failure instanceof Error ? failure.message : "Could not save."),
      },
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-end gap-3">
        <button
          type="button"
          className="text-xs text-ink underline underline-offset-4 hover:text-accent"
          onClick={() => setEditing((open) => !open)}
        >
          {editing ? "Close" : "Edit"}
        </button>
        <button
          type="button"
          className="text-xs text-ink underline underline-offset-4 hover:text-accent disabled:opacity-50"
          disabled={update.isPending}
          onClick={() =>
            update.mutate(
              { categoryId: category.id, input: { active: !category.active } },
              {
                onSuccess: (saved) =>
                  toast({
                    title: saved.active ? "Category shown" : "Category hidden",
                    description: saved.active
                      ? "It is visible in the storefront again."
                      : "Products stay attached; the category is hidden from the storefront.",
                    variant: "success",
                  }),
                onError: (failure) =>
                  setError(failure instanceof Error ? failure.message : "Could not update."),
              },
            )
          }
        >
          {category.active ? "Hide" : "Show"}
        </button>
      </div>
      {editing ? (
        <div className="space-y-2 text-left">
          <label className="block text-xs text-muted">
            Name
            <Input
              className="mt-1 h-8"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={200}
              aria-label={`Name of ${category.name}`}
            />
          </label>
          <label className="block text-xs text-muted">
            Sort order
            <Input
              className="mt-1 h-8 w-24"
              type="number"
              min={0}
              step={1}
              value={sortOrder}
              onChange={(event) => setSortOrder(event.target.value)}
              aria-label={`Sort order of ${category.name}`}
            />
          </label>
          <Button type="button" size="sm" loading={update.isPending} onClick={save}>
            Save
          </Button>
        </div>
      ) : null}
      {error ? (
        <p role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
    </div>
  )
}

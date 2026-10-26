"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

/** One variant row while a product is being created. */
export interface VariantDraft {
  /** Stable across renames, so React keeps each row's DOM (and focus). */
  key: string
  color: string
  size: string
  sku: string
  /** Kept as text so a half-typed or empty field is visible, not coerced to 0. */
  stock: string
}

let sequence = 0

export function newVariantDraft(): VariantDraft {
  sequence += 1
  return { key: `v${sequence}`, color: "", size: "", sku: "", stock: "0" }
}

export function parseStock(value: string): number | null {
  const trimmed = value.trim()
  if (trimmed === "") return null
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : null
}

/** The combination the database treats as unique: size and colour together. */
function combination(draft: VariantDraft): string {
  return `${draft.color.trim().toLowerCase()}|${draft.size.trim().toLowerCase()}`
}

/**
 * Validates every row. Returns a message per row key, so the problem is shown
 * against the row that caused it rather than as one vague form error.
 */
export function validateVariantDrafts(
  drafts: readonly VariantDraft[],
): Record<string, string> {
  const errors: Record<string, string> = {}
  const seen = new Map<string, string>()

  for (const draft of drafts) {
    if (parseStock(draft.stock) === null) {
      errors[draft.key] = "Stock must be a whole number, zero or more."
      continue
    }
    const key = combination(draft)
    const firstKey = seen.get(key)
    if (firstKey !== undefined) {
      // Flag the later duplicate; the first row is the one to keep.
      errors[draft.key] = "Another row already has this size and colour."
      continue
    }
    seen.set(key, draft.key)
  }
  return errors
}

/**
 * Variant rows for a product being created (PROJECT_SPEC §6). The API accepts
 * up to 100 variants per product, so the form lets the operator enter the sizes
 * and colours up front instead of saving a half-finished product first and
 * adding the rest one by one on the edit page.
 */
export function VariantDraftEditor({
  drafts,
  errors,
  disabled = false,
  onChange,
}: {
  drafts: readonly VariantDraft[]
  errors: Record<string, string>
  disabled?: boolean
  onChange: (next: VariantDraft[]) => void
}) {
  function update(key: string, patch: Partial<VariantDraft>) {
    onChange(drafts.map((draft) => (draft.key === key ? { ...draft, ...patch } : draft)))
  }

  function remove(key: string) {
    onChange(drafts.filter((draft) => draft.key !== key))
  }

  return (
    <div className="space-y-3">
      <ul className="space-y-3">
        {drafts.map((draft, index) => (
          <li key={draft.key} className="border border-line bg-surface p-3">
            <div className="grid gap-3 sm:grid-cols-[1fr_1fr_1fr_7rem_auto]">
              <label className="space-y-1">
                <span className="block text-xs text-muted">Colour</span>
                <Input
                  value={draft.color}
                  onChange={(event) => update(draft.key, { color: event.target.value })}
                  maxLength={64}
                  placeholder="Sand"
                  aria-label={`Colour, variant ${index + 1}`}
                  disabled={disabled}
                />
              </label>
              <label className="space-y-1">
                <span className="block text-xs text-muted">Size</span>
                <Input
                  value={draft.size}
                  onChange={(event) => update(draft.key, { size: event.target.value })}
                  maxLength={32}
                  placeholder="M"
                  aria-label={`Size, variant ${index + 1}`}
                  disabled={disabled}
                />
              </label>
              <label className="space-y-1">
                <span className="block text-xs text-muted">SKU (optional)</span>
                <Input
                  value={draft.sku}
                  onChange={(event) => update(draft.key, { sku: event.target.value })}
                  maxLength={64}
                  placeholder="Generated if blank"
                  aria-label={`SKU, variant ${index + 1}`}
                  disabled={disabled}
                />
              </label>
              <label className="space-y-1">
                <span className="block text-xs text-muted">Stock</span>
                <Input
                  type="number"
                  min={0}
                  step={1}
                  value={draft.stock}
                  onChange={(event) => update(draft.key, { stock: event.target.value })}
                  aria-label={`Opening stock, variant ${index + 1}`}
                  aria-invalid={errors[draft.key] ? true : undefined}
                  disabled={disabled}
                />
              </label>
              <div className="flex items-end">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={disabled || drafts.length === 1}
                  onClick={() => remove(draft.key)}
                  aria-label={`Remove variant ${index + 1}`}
                  title={
                    drafts.length === 1
                      ? "A product needs at least one variant"
                      : undefined
                  }
                >
                  Remove
                </Button>
              </div>
            </div>
            {errors[draft.key] ? (
              <p role="alert" className="mt-2 text-xs text-accent">
                Variant {index + 1}: {errors[draft.key]}
              </p>
            ) : null}
          </li>
        ))}
      </ul>

      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled || drafts.length >= 100}
        onClick={() => onChange([...drafts, newVariantDraft()])}
      >
        Add another variant
      </Button>
      {drafts.length >= 100 ? (
        <p className="text-xs text-muted">A product may have at most 100 variants.</p>
      ) : null}
    </div>
  )
}

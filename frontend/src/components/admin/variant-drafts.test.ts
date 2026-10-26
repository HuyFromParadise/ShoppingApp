import { describe, expect, it } from "vitest"
import { newVariantDraft, parseStock, validateVariantDrafts, type VariantDraft } from "./variant-drafts"

function draft(overrides: Partial<VariantDraft> = {}): VariantDraft {
  return { ...newVariantDraft(), color: "Sand", size: "M", stock: "3", ...overrides }
}

describe("variant draft rules", () => {
  it("gives every row a distinct key so React keeps row state", () => {
    const a = newVariantDraft()
    const b = newVariantDraft()
    expect(a.key).not.toBe(b.key)
  })

  it("accepts a whole, non-negative stock count only", () => {
    expect(parseStock("0")).toBe(0)
    expect(parseStock(" 12 ")).toBe(12)
    expect(parseStock("")).toBeNull()
    expect(parseStock("-1")).toBeNull()
    expect(parseStock("1.5")).toBeNull()
    expect(parseStock("abc")).toBeNull()
  })

  it("flags the row whose stock is invalid, not the others", () => {
    const good = draft()
    const bad = draft({ color: "Black", stock: "" })
    const errors = validateVariantDrafts([good, bad])

    expect(errors[good.key]).toBeUndefined()
    expect(errors[bad.key]).toBe("Stock must be a whole number, zero or more.")
  })

  it("rejects two rows with the same size and colour", () => {
    const first = draft()
    const duplicate = draft({ stock: "5" })
    const errors = validateVariantDrafts([first, duplicate])

    // The first row is kept; the later duplicate is the one to change.
    expect(errors[first.key]).toBeUndefined()
    expect(errors[duplicate.key]).toBe("Another row already has this size and colour.")
  })

  it("compares the size and colour case-insensitively and ignoring padding", () => {
    const errors = validateVariantDrafts([
      draft({ color: "Sand", size: "M" }),
      draft({ color: " sand ", size: "m" }),
    ])
    expect(Object.keys(errors)).toHaveLength(1)
  })

  it("allows the same colour in different sizes, and the same size in different colours", () => {
    expect(validateVariantDrafts([
      draft({ color: "Sand", size: "M" }),
      draft({ color: "Sand", size: "L" }),
      draft({ color: "Black", size: "M" }),
    ])).toEqual({})
  })
})

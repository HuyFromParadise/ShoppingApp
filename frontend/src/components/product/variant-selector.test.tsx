import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import type { ProductVariant } from "@/types/catalog"
import { VariantSelector } from "./variant-selector"

const variants: ProductVariant[] = [
  {
    id: "v1",
    sku: "TEE-BLK-M",
    size: "M",
    color: "Black",
    priceMinor: 499000,
    compareAtPriceMinor: null,
    stockQuantity: 0,
    inStock: false,
  },
  {
    id: "v2",
    sku: "TEE-BLK-L",
    size: "L",
    color: "Black",
    priceMinor: 499000,
    compareAtPriceMinor: 599000,
    stockQuantity: 3,
    inStock: true,
  },
  {
    id: "v3",
    sku: "TEE-WHT-M",
    size: "M",
    color: "White",
    priceMinor: 529000,
    compareAtPriceMinor: null,
    stockQuantity: 8,
    inStock: true,
  },
]

describe("VariantSelector", () => {
  it("disables sold-out combinations and shows the selected variant details", () => {
    render(<VariantSelector variants={variants} />)

    expect(screen.getByText("Select a colour")).toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "Black" }))
    expect(screen.getByRole("button", { name: "M" })).toBeDisabled()

    fireEvent.click(screen.getByRole("button", { name: "L" }))
    expect(screen.getByText(/TEE-BLK-L/)).toBeInTheDocument()
    expect(screen.getByText("Only 3 left")).toBeInTheDocument()
    expect(screen.getByText(/499\.000/)).toBeInTheDocument()
  })

  it("keeps unavailable options non-selectable", () => {
    render(<VariantSelector variants={variants} />)

    const soldOut = screen.getByRole("button", { name: "Black" })
    const soldOutSize = screen.getByRole("button", { name: "M" })
    fireEvent.click(soldOut)
    fireEvent.click(soldOutSize)

    expect(soldOutSize).toBeDisabled()
    expect(screen.queryByText("TEE-BLK-M")).not.toBeInTheDocument()
  })

  it("auto-selects a single available colour and size", () => {
    const single = variants.filter((variant) => variant.id === "v2")
    render(<VariantSelector variants={single} />)

    expect(screen.getByText(/TEE-BLK-L/)).toBeInTheDocument()
    expect(screen.getByText("Only 3 left")).toBeInTheDocument()
  })
})

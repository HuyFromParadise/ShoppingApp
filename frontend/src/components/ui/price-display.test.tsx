import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { PriceDisplay } from "./price-display"

function normalized(value: string | null): string {
  return (value ?? "").replace(/\s/g, " ")
}

describe("PriceDisplay", () => {
  it("renders the server amount without arithmetic", () => {
    render(<PriceDisplay amountMinor={499000} />)
    const text = normalized(screen.getByText(/499\.000/).textContent)
    expect(text).toContain("499.000")
    expect(text).toContain("₫")
  })

  it("shows a struck-through original price only when it is higher", () => {
    const { rerender } = render(
      <PriceDisplay amountMinor={299000} compareAtMinor={499000} />,
    )
    const original = document.querySelector("s")
    expect(original).not.toBeNull()
    expect(original).toHaveTextContent("Giá gốc")
    expect(normalized(original?.textContent ?? "")).toContain("499.000")

    rerender(<PriceDisplay amountMinor={299000} compareAtMinor={299000} />)
    expect(document.querySelector("s")).toBeNull()
  })
})

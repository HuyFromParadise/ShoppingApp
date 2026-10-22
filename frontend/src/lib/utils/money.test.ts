import { describe, expect, it } from "vitest"
import { formatMoney } from "./money"

function normalized(value: string): string {
  return value.replace(/\s/g, " ")
}

describe("formatMoney", () => {
  it("formats VND minor units without dividing (VND has no subunit)", () => {
    expect(normalized(formatMoney(499000))).toContain("499.000")
    expect(normalized(formatMoney(499000))).toContain("₫")
  })

  it("formats zero and large amounts", () => {
    expect(normalized(formatMoney(0))).toContain("0")
    expect(normalized(formatMoney(1250000))).toContain("1.250.000")
  })

  it("honours an explicit currency", () => {
    const formatted = normalized(formatMoney(1000, "JPY"))
    expect(formatted).toContain("1.000")
    expect(formatted).not.toContain("₫")
  })
})

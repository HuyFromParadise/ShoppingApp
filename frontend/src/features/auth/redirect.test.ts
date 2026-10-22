import { describe, expect, it } from "vitest"
import { safeRedirectPath } from "./redirect"

describe("safeRedirectPath", () => {
  it("keeps a same-origin path", () => {
    expect(safeRedirectPath("/account/orders")).toBe("/account/orders")
    expect(safeRedirectPath("/shop?category=shirts")).toBe("/shop?category=shirts")
  })

  it("turns a relative path into an absolute one", () => {
    expect(safeRedirectPath("account")).toBe("/account")
  })

  it("rejects an absolute url to another origin", () => {
    expect(safeRedirectPath("https://evil.example/steal")).toBeUndefined()
    expect(safeRedirectPath("http://evil.example")).toBeUndefined()
  })

  it("rejects a protocol-relative url", () => {
    expect(safeRedirectPath("//evil.example")).toBeUndefined()
  })

  it("rejects a backslash authority, which browsers read as a host", () => {
    expect(safeRedirectPath("/\\evil.example")).toBeUndefined()
    expect(safeRedirectPath("/\\t/evil.example")).toBeUndefined()
    expect(safeRedirectPath("/\\\\evil.example")).toBeUndefined()
  })

  it("rejects a javascript url", () => {
    expect(safeRedirectPath("javascript:alert(1)")).toBeUndefined()
  })

  it("rejects empty and non-string values", () => {
    expect(safeRedirectPath("")).toBeUndefined()
    expect(safeRedirectPath(undefined)).toBeUndefined()
    expect(safeRedirectPath(null)).toBeUndefined()
    expect(safeRedirectPath(42)).toBeUndefined()
  })

  it("uses the first value of a repeated parameter", () => {
    expect(safeRedirectPath(["/account", "/admin"])).toBe("/account")
  })
})

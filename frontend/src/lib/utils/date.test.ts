import { describe, expect, it } from "vitest"
import { formatDate, formatDateTime } from "./date"

describe("date formatting", () => {
  it("formats an ISO date for Vietnamese readers", () => {
    expect(formatDate("2026-10-21")).toBe("21/10/2026")
  })

  it("returns a placeholder for invalid input", () => {
    expect(formatDate("not-a-date")).toBe("—")
    expect(formatDateTime("not-a-date")).toBe("—")
  })

  it("includes a time component for timestamps", () => {
    expect(formatDateTime("2026-10-21T02:10:00Z")).not.toBe("—")
  })
})

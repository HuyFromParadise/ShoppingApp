import { describe, expect, it } from "vitest"
import {
  ADMIN_ORDER_TARGETS,
  adminOrderActions,
  productStatusLabel,
} from "./admin"

/**
 * The UI mirrors the backend order state machine (DESIGN_REFERENCE §7). These
 * tests pin the offered actions to the contract, so a UI change cannot quietly
 * offer a transition the server will reject.
 */
describe("admin order actions", () => {
  it("matches the documented transitions for every status", () => {
    for (const [status, targets] of Object.entries(ADMIN_ORDER_TARGETS)) {
      const offered = adminOrderActions(status).map((action) => action.target)
      expect(offered).toEqual(targets)
    }
  })

  it("offers nothing for closed orders", () => {
    expect(adminOrderActions("CANCELLED")).toEqual([])
    expect(adminOrderActions("RETURNED")).toEqual([])
  })

  it("does not offer a return request, because only the customer can file one", () => {
    const allTargets = Object.values(ADMIN_ORDER_TARGETS).flat()
    expect(allTargets).not.toContain("RETURN_REQUESTED")
  })

  it("warns before a destructive or money-moving action", () => {
    const cancel = adminOrderActions("PENDING_PAYMENT").find((a) => a.target === "CANCELLED")
    expect(cancel).toMatchObject({ destructive: true, confirm: true })

    const acceptReturn = adminOrderActions("RETURN_REQUESTED").find((a) => a.target === "RETURNED")
    expect(acceptReturn).toMatchObject({ confirm: true })

    const ship = adminOrderActions("PROCESSING").find((a) => a.target === "SHIPPED")
    expect(ship).toMatchObject({ destructive: false, confirm: false })
  })

  it("offers nothing for an unknown status rather than guessing", () => {
    expect(adminOrderActions("SOMETHING_NEW")).toEqual([])
  })
})

describe("product status labels", () => {
  it("speaks the operator's language", () => {
    expect(productStatusLabel("DRAFT")).toBe("Draft")
    expect(productStatusLabel("ACTIVE")).toBe("Live")
    expect(productStatusLabel("ARCHIVED")).toBe("Archived")
    expect(productStatusLabel("WHATEVER")).toBe("WHATEVER")
  })
})

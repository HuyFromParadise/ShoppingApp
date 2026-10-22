import { describe, expect, it } from "vitest"
import { addressSchema, profileSchema } from "./account"

describe("profileSchema", () => {
  it("accepts a name and an optional phone", () => {
    const parsed = profileSchema.parse({
      firstName: "  An ",
      lastName: "Nguyen",
      phone: "0901234567",
    })

    expect(parsed.firstName).toBe("An")
  })

  it("requires both names", () => {
    const result = profileSchema.safeParse({ firstName: "  ", lastName: "" })

    expect(result.success).toBe(false)
    if (!result.success) {
      const fields = result.error.flatten().fieldErrors
      expect(fields.firstName).toBeDefined()
      expect(fields.lastName).toBeDefined()
    }
  })

  it("rejects a phone number longer than 30 characters", () => {
    const result = profileSchema.safeParse({
      firstName: "An",
      lastName: "Nguyen",
      phone: "0".repeat(31),
    })

    expect(result.success).toBe(false)
  })
})

describe("addressSchema", () => {
  const valid = {
    recipientName: "An Nguyen",
    phone: "0901234567",
    addressLine1: "12 Le Loi",
    addressLine2: "",
    ward: "Ben Thanh",
    district: "District 1",
    cityOrProvince: "Ho Chi Minh City",
    postalCode: "700000",
    isDefault: false,
  }

  it("accepts a complete address", () => {
    const parsed = addressSchema.parse(valid)

    expect(parsed.recipientName).toBe("An Nguyen")
    expect(parsed.isDefault).toBe(false)
  })

  it("requires the recipient, the first line and the city", () => {
    const result = addressSchema.safeParse({
      ...valid,
      recipientName: "",
      addressLine1: " ",
      cityOrProvince: "",
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      const fields = result.error.flatten().fieldErrors
      expect(fields.recipientName).toBeDefined()
      expect(fields.addressLine1).toBeDefined()
      expect(fields.cityOrProvince).toBeDefined()
    }
  })

  it("rejects an address line longer than the column allows", () => {
    const result = addressSchema.safeParse({ ...valid, addressLine1: "x".repeat(256) })

    expect(result.success).toBe(false)
  })
})

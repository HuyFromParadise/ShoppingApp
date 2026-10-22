import { describe, expect, it } from "vitest"
import { loginSchema, registerSchema } from "./auth"

describe("loginSchema", () => {
  it("accepts a valid email and password", () => {
    const parsed = loginSchema.parse({
      email: "  Huy@example.com ",
      password: "correct horse",
    })

    expect(parsed.email).toBe("Huy@example.com")
  })

  it("rejects a malformed email", () => {
    const result = loginSchema.safeParse({ email: "nope", password: "secret" })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.email).toBeDefined()
    }
  })

  it("requires a password", () => {
    const result = loginSchema.safeParse({ email: "huy@example.com", password: "" })

    expect(result.success).toBe(false)
  })
})

describe("registerSchema", () => {
  const valid = {
    email: "huy@example.com",
    password: "a-long-enough-password",
    confirmPassword: "a-long-enough-password",
    firstName: "Huy",
    lastName: "Pham",
  }

  it("accepts valid registration values", () => {
    const parsed = registerSchema.parse(valid)

    expect(parsed.email).toBe("huy@example.com")
    expect(parsed.firstName).toBe("Huy")
  })

  it("treats the phone number as optional", () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
    expect(
      registerSchema.safeParse({ ...valid, phone: "0901234567" }).success,
    ).toBe(true)
  })

  it("rejects a password shorter than 10 characters", () => {
    const result = registerSchema.safeParse({
      ...valid,
      password: "short",
      confirmPassword: "short",
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.password).toBeDefined()
    }
  })

  it("rejects a password longer than 72 characters", () => {
    const password = "a".repeat(73)
    const result = registerSchema.safeParse({
      ...valid,
      password,
      confirmPassword: password,
    })

    expect(result.success).toBe(false)
  })

  it("rejects a confirmation that does not match", () => {
    const result = registerSchema.safeParse({
      ...valid,
      confirmPassword: "something-else-entirely",
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.confirmPassword).toBeDefined()
    }
  })

  it("requires the name fields", () => {
    const result = registerSchema.safeParse({
      ...valid,
      firstName: "  ",
      lastName: "",
    })

    expect(result.success).toBe(false)
  })
})

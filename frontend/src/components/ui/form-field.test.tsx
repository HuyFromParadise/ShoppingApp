import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { FormField } from "./form-field"
import { Input } from "./input"

describe("FormField", () => {
  it("associates the label, hint and error with the control", () => {
    render(
      <FormField
        id="email"
        label="Email"
        hint="We never share your email."
        error="Must be a valid email address"
        required
      >
        <Input type="email" />
      </FormField>,
    )

    const input = screen.getByLabelText(/email/i)
    expect(input).toHaveAttribute("id", "email")
    expect(input).toHaveAttribute("aria-invalid", "true")
    expect(input).toHaveAttribute("aria-required", "true")
    expect(input).toHaveAttribute(
      "aria-describedby",
      "email-hint email-error",
    )
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Must be a valid email address",
    )
  })

  it("omits error attributes when valid", () => {
    render(
      <FormField id="name" label="Name">
        <Input />
      </FormField>,
    )

    const input = screen.getByLabelText("Name")
    expect(input).not.toHaveAttribute("aria-invalid")
    expect(input).not.toHaveAttribute("aria-describedby")
  })
})

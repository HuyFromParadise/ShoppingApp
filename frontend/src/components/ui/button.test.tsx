import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import { Button } from "./button"

describe("Button", () => {
  it("renders an accessible button and fires onClick", () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Save</Button>)
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it("disables interaction and exposes busy state while loading", () => {
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        Saving…
      </Button>,
    )
    const button = screen.getByRole("button", { name: /saving/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute("aria-busy", "true")
    fireEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it("keeps a submit type when explicitly requested", () => {
    render(<Button type="submit">Submit</Button>)
    expect(screen.getByRole("button", { name: "Submit" })).toHaveAttribute(
      "type",
      "submit",
    )
  })
})

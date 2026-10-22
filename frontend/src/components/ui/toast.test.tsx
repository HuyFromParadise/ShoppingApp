import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { ToastProvider, useToast } from "./toast"

function ToastTrigger() {
  const { toast } = useToast()
  return (
    <button
      type="button"
      onClick={() => toast({ title: "Added to cart", variant: "success" })}
    >
      Add
    </button>
  )
}

describe("ToastProvider", () => {
  it("announces a toast and allows dismissing it", () => {
    render(
      <ToastProvider>
        <ToastTrigger />
      </ToastProvider>,
    )

    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    expect(screen.getByRole("status")).toHaveTextContent("Added to cart")

    fireEvent.click(screen.getByRole("button", { name: "Dismiss notification" }))
    expect(screen.queryByText("Added to cart")).not.toBeInTheDocument()
  })

  it("throws when useToast is used outside the provider", () => {
    const original = console.error
    console.error = () => undefined
    try {
      expect(() => render(<ToastTrigger />)).toThrow(
        "useToast must be used within a ToastProvider",
      )
    } finally {
      console.error = original
    }
  })
})

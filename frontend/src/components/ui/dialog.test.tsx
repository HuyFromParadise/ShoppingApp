import { fireEvent, render, screen } from "@testing-library/react"
import * as React from "react"
import { describe, expect, it } from "vitest"
import { Dialog } from "./dialog"

function Harness() {
  const [open, setOpen] = React.useState(false)
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        Open dialog
      </button>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title="Remove item"
        description="This cannot be undone."
      >
        <button type="button">Confirm</button>
      </Dialog>
    </div>
  )
}

describe("Dialog", () => {
  it("opens with a labelled modal and closes on Escape", () => {
    render(<Harness />)

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole("button", { name: "Open dialog" }))

    const dialog = screen.getByRole("dialog", { name: "Remove item" })
    expect(dialog).toHaveAttribute("aria-modal", "true")
    expect(dialog).toHaveAttribute("aria-describedby")
    expect(screen.getByText("This cannot be undone.")).toBeInTheDocument()

    fireEvent.keyDown(document, { key: "Escape" })
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })

  it("closes from the accessible close button", () => {
    render(<Harness />)
    fireEvent.click(screen.getByRole("button", { name: "Open dialog" }))
    fireEvent.click(screen.getByRole("button", { name: "Close dialog" }))
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })
})

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import AdminCategoriesPage from "./page"

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/categories",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const categories = [
  {
    id: "c1",
    name: "Shirts",
    slug: "shirts",
    description: null,
    parentId: null,
    active: true,
    sortOrder: 1,
    productCount: 4,
  },
  {
    id: "c2",
    name: "Outerwear",
    slug: "outerwear",
    description: null,
    parentId: "c1",
    active: false,
    sortOrder: 2,
    productCount: 0,
  },
]

function stubServer(overrides: { post?: Response; patch?: Response; list?: Response } = {}) {
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET"
    if (method === "POST") {
      return Promise.resolve(
        overrides.post ?? jsonResponse({ data: categories[0], message: "Success" }, 201),
      )
    }
    if (method === "PATCH") {
      return Promise.resolve(
        overrides.patch ?? jsonResponse({ data: { ...categories[0], active: false }, message: "Success" }),
      )
    }
    return Promise.resolve(overrides.list ?? jsonResponse({ data: categories, message: "Success" }))
  })
  vi.stubGlobal("fetch", mock)
  return mock
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AdminCategoriesPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/** The table row for a category, chosen by its slug cell (names repeat as parents). */
async function rowFor(slug: string): Promise<HTMLElement> {
  // the slug cell renders as "/shirts"
  const cells = await screen.findAllByText(`/${slug}`, { exact: true })
  return cells[0].closest("tr") as HTMLElement
}

describe("admin categories page", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists every category with visibility and product count", async () => {
    stubServer()
    renderPage()

    const table = screen.getByRole("table", { name: "Categories" })
    const shirts = await rowFor("shirts")
    expect(within(shirts).getByText("Visible")).toBeInTheDocument()
    expect(within(shirts).getByRole("link", { name: "4" })).toHaveAttribute(
      "href",
      "/admin/products?categoryId=c1",
    )

    const outer = await rowFor("outerwear")
    expect(within(outer).getByText("Hidden")).toBeInTheDocument()
    // the parent name is shown, not its id
    expect(within(outer).getByText("Shirts")).toBeInTheDocument()
    expect(screen.queryByText("Delete")).not.toBeInTheDocument()
  })

  it("creates a category and sends only what was entered", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findAllByText("Shirts")
    fireEvent.click(screen.getByRole("button", { name: "New category" }))
    fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: "Knitwear" } })
    fireEvent.click(screen.getByRole("button", { name: "Create category" }))

    await waitFor(() => {
      const call = mock.mock.calls.find(([, init]) => init?.method === "POST")
      expect(call).toBeDefined()
      const body = JSON.parse(String(call?.[1]?.body))
      expect(body).toEqual({ name: "Knitwear", active: true })
    })
  })

  it("rejects a category with no name before calling the server", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findAllByText("Shirts")
    fireEvent.click(screen.getByRole("button", { name: "New category" }))
    fireEvent.click(screen.getByRole("button", { name: "Create category" }))

    expect(await screen.findByText("A name is required.")).toBeInTheDocument()
    expect(mock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
  })

  it("surfaces a duplicate slug from the server", async () => {
    stubServer({
      post: jsonResponse(
        {
          timestamp: "x",
          status: 400,
          code: "VALIDATION_ERROR",
          message: "Request validation failed",
          fieldErrors: { slug: "Already in use" },
        },
        400,
      ),
    })
    renderPage()

    await screen.findAllByText("Shirts")
    fireEvent.click(screen.getByRole("button", { name: "New category" }))
    fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: "Knitwear" } })
    fireEvent.click(screen.getByRole("button", { name: "Create category" }))

    expect(await screen.findByText("Already in use")).toBeInTheDocument()
  })

  it("hides a category through a patch, never a delete", async () => {
    const mock = stubServer()
    renderPage()

    const table = screen.getByRole("table", { name: "Categories" })
    const shirts = await rowFor("shirts")
    fireEvent.click(within(shirts).getByRole("button", { name: "Hide" }))

    await waitFor(() => {
      const call = mock.mock.calls.find(([, init]) => init?.method === "PATCH")
      expect(call).toBeDefined()
      expect(String(call?.[0])).toBe("http://localhost:8080/api/v1/admin/categories/c1")
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({ active: false })
    })
    expect(await screen.findByText("Category hidden")).toBeInTheDocument()
  })

  it("renames a category and validates the sort order", async () => {
    const mock = stubServer()
    renderPage()

    const table = screen.getByRole("table", { name: "Categories" })
    const shirts = await rowFor("shirts")
    fireEvent.click(within(shirts).getByRole("button", { name: "Edit" }))

    fireEvent.change(screen.getByLabelText("Sort order of Shirts"), {
      target: { value: "-1" },
    })
    fireEvent.click(within(shirts).getByRole("button", { name: "Save" }))
    expect(await screen.findByText("Sort order must be zero or more.")).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText("Name of Shirts"), {
      target: { value: "Shirting" },
    })
    fireEvent.change(screen.getByLabelText("Sort order of Shirts"), { target: { value: "3" } })
    fireEvent.click(within(shirts).getByRole("button", { name: "Save" }))

    await waitFor(() => {
      const call = mock.mock.calls.find(([, init]) => init?.method === "PATCH")
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({ name: "Shirting", sortOrder: 3 })
    })
  })

  it("offers a retry when the list fails", async () => {
    stubServer({
      list: jsonResponse({ timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" }, 500),
    })
    renderPage()

    expect(await screen.findByText("We could not load the categories")).toBeInTheDocument()
  })
})

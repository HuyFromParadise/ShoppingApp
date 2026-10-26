import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import NewProductPage from "./page"

const push = vi.fn()

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/admin/products/new",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const categories = {
  data: [
    { id: "c1", name: "Shirts", slug: "shirts", active: true, productCount: 2 },
  ],
  message: "Success",
}

const created = {
  data: { id: "p9", name: "Linen Blazer" },
  message: "Success",
}

function stubServer(options: { post?: (body: FormData | string) => Response } = {}) {
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const target = String(url)
    if (target.endsWith("/admin/categories")) {
      return Promise.resolve(jsonResponse(categories))
    }
    if (target.endsWith("/admin/products") && (init?.method ?? "GET") === "POST") {
      if (options.post) return Promise.resolve(options.post(init?.body as FormData | string))
      return Promise.resolve(jsonResponse(created, 201))
    }
    return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
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
        <NewProductPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function productPosts(mock: ReturnType<typeof stubServer>) {
  return mock.mock.calls.filter(
    ([url, init]) =>
      String(url).endsWith("/admin/products") && init?.method === "POST",
  )
}

async function fillRequiredFields() {
  await screen.findByLabelText(/^Name/)
  fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: "Linen Blazer" } })
  fireEvent.change(screen.getByLabelText(/Base price/), { target: { value: "899000" } })
}

function pngFile(name = "blazer.png") {
  return new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], name, { type: "image/png" })
}

describe("new product page", () => {
  beforeEach(() => {
    push.mockReset()
    // jsdom has no object-URL support; the preview only needs a string.
    vi.stubGlobal("URL", Object.assign(URL, {
      createObjectURL: vi.fn(() => "blob:preview"),
      revokeObjectURL: vi.fn(),
    }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("creates a product and its image in one multipart request", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Choose the first image"), {
      target: { files: [pngFile()] },
    })
    fireEvent.change(screen.getByLabelText(/Alt text/), {
      target: { value: " A sand blazer " },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    await waitFor(() => expect(productPosts(mock)).toHaveLength(1))
    const body = productPosts(mock)[0][1]?.body as FormData
    expect(body).toBeInstanceOf(FormData)

    const productPart = body.get("product")
    expect(productPart).toBeInstanceOf(Blob)
    const parsed = JSON.parse(await blobText(productPart as Blob))
    expect(parsed.name).toBe("Linen Blazer")
    expect(parsed.categoryId).toBe("c1")
    expect(parsed.basePrice).toBe(899000)
    expect(parsed.variants).toHaveLength(1)

    expect((body.get("file") as File).name).toBe("blazer.png")
    expect(body.get("altText")).toBe("A sand blazer")

    // The browser must set the multipart boundary itself.
    const headers = (productPosts(mock)[0][1]?.headers ?? {}) as Record<string, string>
    expect(headers["Content-Type"]).toBeUndefined()

    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/products/p9"))
  })

  it("still creates a product when no image is chosen", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    await waitFor(() => expect(productPosts(mock)).toHaveLength(1))
    // Without a file the request stays plain JSON, so the endpoint is unchanged.
    const init = productPosts(mock)[0][1]
    expect(typeof init?.body).toBe("string")
    expect(JSON.parse(String(init?.body))).toMatchObject({ name: "Linen Blazer" })
  })

  it("refuses a non-image file before contacting the server", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Choose the first image"), {
      target: {
        files: [new File(["<script>"], "payload.txt", { type: "text/plain" })],
      },
    })

    expect(screen.getByText("Choose a JPEG, PNG or WebP image.")).toBeInTheDocument()
    fireEvent.click(screen.getByRole("button", { name: "Create product" }))
    expect(productPosts(mock)).toHaveLength(0)
  })

  it("refuses a file over 5 MB before contacting the server", async () => {
    stubServer()
    renderPage()

    await fillRequiredFields()
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "big.png", {
      type: "image/png",
    })
    fireEvent.change(screen.getByLabelText("Choose the first image"), {
      target: { files: [big] },
    })

    expect(screen.getByText("Images must be 5 MB or smaller.")).toBeInTheDocument()
  })

  it("reports a file the server rejected, and keeps the form", async () => {
    stubServer({
      post: () =>
        jsonResponse(
          {
            timestamp: "x",
            status: 400,
            code: "VALIDATION_ERROR",
            message: "Only JPEG, PNG or WebP images are accepted",
            fieldErrors: { file: "Only JPEG, PNG or WebP images are accepted" },
          },
          400,
        ),
    })
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Choose the first image"), {
      target: { files: [pngFile("rename.jpg")] },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    // The message appears on the image field and in the toast, so match loosely.
    expect(
      (await screen.findAllByText("Only JPEG, PNG or WebP images are accepted")).length,
    ).toBeGreaterThan(0)
    expect(push).not.toHaveBeenCalled()
    expect(screen.getByRole("button", { name: "Create product" })).toBeInTheDocument()
  })
  it("sends every variant row the operator entered", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Colour, variant 1"), { target: { value: "Sand" } })
    fireEvent.change(screen.getByLabelText("Size, variant 1"), { target: { value: "M" } })
    fireEvent.change(screen.getByLabelText("Opening stock, variant 1"), { target: { value: "4" } })

    fireEvent.click(screen.getByRole("button", { name: "Add another variant" }))
    fireEvent.change(screen.getByLabelText("Colour, variant 2"), { target: { value: "Sand" } })
    fireEvent.change(screen.getByLabelText("Size, variant 2"), { target: { value: "L" } })
    fireEvent.change(screen.getByLabelText("Opening stock, variant 2"), { target: { value: "7" } })

    fireEvent.click(screen.getByRole("button", { name: "Add another variant" }))
    fireEvent.change(screen.getByLabelText("Colour, variant 3"), { target: { value: "Black" } })
    fireEvent.change(screen.getByLabelText("Size, variant 3"), { target: { value: "M" } })
    fireEvent.change(screen.getByLabelText("SKU, variant 3"), { target: { value: "BLZ-BLK-M" } })
    fireEvent.change(screen.getByLabelText("Opening stock, variant 3"), { target: { value: "0" } })

    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    await waitFor(() => expect(productPosts(mock)).toHaveLength(1))
    // No image chosen, so the create is plain JSON; every row still travels.
    const parsed = JSON.parse(String(productPosts(mock)[0][1]?.body))
    expect(parsed.variants).toEqual([
      { color: "Sand", size: "M", stockQuantity: 4, active: true },
      { color: "Sand", size: "L", stockQuantity: 7, active: true },
      { color: "Black", size: "M", sku: "BLZ-BLK-M", stockQuantity: 0, active: true },
    ])
  })

  it("blocks a duplicate size and colour and points at the row", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Colour, variant 1"), { target: { value: "Sand" } })
    fireEvent.change(screen.getByLabelText("Size, variant 1"), { target: { value: "M" } })
    fireEvent.click(screen.getByRole("button", { name: "Add another variant" }))
    fireEvent.change(screen.getByLabelText("Colour, variant 2"), { target: { value: "sand" } })
    fireEvent.change(screen.getByLabelText("Size, variant 2"), { target: { value: "m" } })

    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    expect(
      await screen.findByText(/Variant 2: Another row already has this size and colour\./),
    ).toBeInTheDocument()
    expect(productPosts(mock)).toHaveLength(0)
  })

  it("blocks an empty stock in any row", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Opening stock, variant 1"), { target: { value: "" } })
    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    expect(
      await screen.findByText(/Variant 1: Stock must be a whole number, zero or more\./),
    ).toBeInTheDocument()
    expect(productPosts(mock)).toHaveLength(0)
  })

  it("keeps at least one row, and removing a row renumbers the rest", async () => {
    stubServer()
    renderPage()

    await fillRequiredFields()
    expect(screen.getByRole("button", { name: "Remove variant 1" })).toBeDisabled()

    fireEvent.click(screen.getByRole("button", { name: "Add another variant" }))
    fireEvent.change(screen.getByLabelText("Colour, variant 2"), { target: { value: "Navy" } })
    fireEvent.click(screen.getByRole("button", { name: "Remove variant 1" }))

    expect(screen.queryByLabelText("Colour, variant 2")).not.toBeInTheDocument()
    expect((screen.getByLabelText("Colour, variant 1") as HTMLInputElement).value).toBe("Navy")
  })

  it("sends all variant rows together with an image when one is chosen", async () => {
    const mock = stubServer()
    renderPage()

    await fillRequiredFields()
    fireEvent.change(screen.getByLabelText("Colour, variant 1"), { target: { value: "Sand" } })
    fireEvent.change(screen.getByLabelText("Size, variant 1"), { target: { value: "M" } })
    fireEvent.click(screen.getByRole("button", { name: "Add another variant" }))
    fireEvent.change(screen.getByLabelText("Colour, variant 2"), { target: { value: "Navy" } })
    fireEvent.change(screen.getByLabelText("Size, variant 2"), { target: { value: "L" } })
    fireEvent.change(screen.getByLabelText("Choose the first image"), {
      target: { files: [pngFile()] },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create product" }))

    await waitFor(() => expect(productPosts(mock)).toHaveLength(1))
    const body = productPosts(mock)[0][1]?.body as FormData
    expect(body).toBeInstanceOf(FormData)
    const parsed = JSON.parse(await blobText(body.get("product") as Blob))
    expect(parsed.variants).toHaveLength(2)
    expect(parsed.variants[1]).toMatchObject({ color: "Navy", size: "L" })
    expect((body.get("file") as File).name).toBe("blazer.png")
  })
})

/** jsdom's Blob has no text(); read it through FileReader instead. */
function blobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob)
  })
}
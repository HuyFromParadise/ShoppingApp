import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { API_BASE_URL } from "@/lib/api/client"
import { tokenStore } from "@/lib/api/token"
import {
  addVariant,
  changeOrderStatus,
  createCategory,
  createProduct,
  deleteProductImage,
  listOrders,
  listProducts,
  setVariantStock,
  updateCategory,
  updateProduct,
  uploadProductImage,
} from "./api"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

type FetchCall = [input: string, init?: RequestInit]

function stubFetch(...responses: Response[]) {
  const mock = vi.fn((_input: string, _init?: RequestInit) => Promise.resolve(responses.shift() as Response))
  vi.stubGlobal("fetch", mock)
  return mock as unknown as { mock: { calls: FetchCall[] } }
}

function headers(call: FetchCall): Record<string, string> {
  return (call[1]?.headers ?? {}) as Record<string, string>
}

describe("admin api", () => {
  beforeEach(() => tokenStore.set("admin-token"))
  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it("lists products with filters and paging", async () => {
    const mock = stubFetch(
      jsonResponse({
        data: [{ id: "p1", name: "Shirt", status: "DRAFT" }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        message: "Success",
      }),
    )

    const page = await listProducts({ q: "shirt", status: "DRAFT", page: 0, size: 20 })

    expect(page.items).toHaveLength(1)
    expect(mock.mock.calls[0][0]).toBe(
      `${API_BASE_URL}/admin/products?q=shirt&status=DRAFT&page=0&size=20`,
    )
    expect(headers(mock.mock.calls[0]).Authorization).toBe("Bearer admin-token")
  })

  it("creates a product with minor-unit prices only", async () => {
    const mock = stubFetch(jsonResponse({ data: { id: "p1" }, message: "Success" }, 201))

    await createProduct({
      name: "Linen Blazer",
      categoryId: "c1",
      basePrice: 899000,
      variants: [{ size: "M", stockQuantity: 3 }],
    })

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/admin/products`)
    expect(init?.method).toBe("POST")
    expect(JSON.parse(String(init?.body))).toEqual({
      name: "Linen Blazer",
      categoryId: "c1",
      basePrice: 899000,
      variants: [{ size: "M", stockQuantity: 3 }],
    })
  })

  it("patches a product and adds a variant", async () => {
    const mock = stubFetch(
      jsonResponse({ data: { id: "p1" }, message: "Success" }),
      jsonResponse({ data: { id: "p1" }, message: "Success" }, 201),
    )

    await updateProduct("p1", { status: "ACTIVE" })
    await addVariant("p1", { color: "Sand", size: "L", stockQuantity: 2 })

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/admin/products/p1`)
    expect(mock.mock.calls[0][1]?.method).toBe("PATCH")
    expect(mock.mock.calls[1][0]).toBe(`${API_BASE_URL}/admin/products/p1/variants`)
    expect(mock.mock.calls[1][1]?.method).toBe("POST")
  })

  it("sets stock absolutely with a reason", async () => {
    const mock = stubFetch(
      jsonResponse({ data: { id: "v1", stockQuantity: 12 }, message: "Success" }),
    )

    const variant = await setVariantStock("v1", 12, "  Stock count  ")

    expect(variant.stockQuantity).toBe(12)
    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/admin/variants/v1/stock`)
    expect(mock.mock.calls[0][1]?.method).toBe("PATCH")
    expect(JSON.parse(String(mock.mock.calls[0][1]?.body))).toEqual({
      stockQuantity: 12,
      reason: "Stock count",
    })
  })

  it("omits an empty stock reason instead of sending whitespace", async () => {
    const mock = stubFetch(jsonResponse({ data: {}, message: "Success" }))

    await setVariantStock("v1", 0, "   ")

    expect(JSON.parse(String(mock.mock.calls[0][1]?.body))).toEqual({ stockQuantity: 0 })
  })

  it("uploads an image as multipart, not JSON", async () => {
    const mock = stubFetch(jsonResponse({ data: [], message: "Success" }, 201))
    const file = new File([new Uint8Array([0x89, 0x50])], "shirt.png", { type: "image/png" })

    await uploadProductImage("p1", file, "A shirt")

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/admin/products/p1/images`)
    expect(init?.method).toBe("POST")
    expect(init?.body).toBeInstanceOf(FormData)
    const body = init?.body as FormData
    expect(body.get("file")).toBe(file)
    expect(body.get("altText")).toBe("A shirt")
    // The browser must set the multipart boundary itself.
    expect(headers(mock.mock.calls[0])["Content-Type"]).toBeUndefined()
  })

  it("deletes an image with DELETE", async () => {
    const mock = stubFetch(new Response(null, { status: 204 }))

    await deleteProductImage("p1", "i1")

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/admin/products/p1/images/i1`)
    expect(mock.mock.calls[0][1]?.method).toBe("DELETE")
  })

  it("creates and updates categories", async () => {
    const mock = stubFetch(
      jsonResponse({ data: { id: "c1" }, message: "Success" }, 201),
      jsonResponse({ data: { id: "c1", active: false }, message: "Success" }),
    )

    await createCategory({ name: "Outerwear" })
    await updateCategory("c1", { active: false })

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/admin/categories`)
    expect(mock.mock.calls[1][0]).toBe(`${API_BASE_URL}/admin/categories/c1`)
    expect(mock.mock.calls[1][1]?.method).toBe("PATCH")
  })

  it("lists orders with a status filter", async () => {
    const mock = stubFetch(
      jsonResponse({
        data: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        message: "Success",
      }),
    )

    await listOrders({ status: "RETURN_REQUESTED", page: 0, size: 20 })

    expect(mock.mock.calls[0][0]).toBe(
      `${API_BASE_URL}/admin/orders?status=RETURN_REQUESTED&page=0&size=20`,
    )
  })

  it("changes an order status with a note", async () => {
    const mock = stubFetch(jsonResponse({ data: { status: "SHIPPED" }, message: "Success" }))

    await changeOrderStatus("o1", "SHIPPED", " Handed to courier ")

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/admin/orders/o1/status`)
    expect(mock.mock.calls[0][1]?.method).toBe("PATCH")
    expect(JSON.parse(String(mock.mock.calls[0][1]?.body))).toEqual({
      status: "SHIPPED",
      note: "Handed to courier",
    })
  })
})

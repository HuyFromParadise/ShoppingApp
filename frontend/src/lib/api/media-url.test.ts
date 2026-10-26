import { afterEach, describe, expect, it, vi } from "vitest"

/**
 * Stored image keys are root-relative paths served by the backend
 * (`/media/products/…`). The browser would resolve them against the frontend
 * origin, where the file does not exist, so they must be resolved to the API
 * origin first.
 */
describe("mediaUrl", () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  async function load(base?: string) {
    vi.resetModules()
    if (base === undefined) {
      vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "")
    } else {
      vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", base)
    }
    return import("@/lib/api/client")
  }

  it("resolves a stored key to the API origin", async () => {
    const { mediaUrl } = await load("http://localhost:8080/api/v1")
    expect(mediaUrl("/media/products/abc.jpg")).toBe(
      "http://localhost:8080/media/products/abc.jpg",
    )
  })

  it("strips only the /api/v1 base path, never a last segment", async () => {
    // A base without a version keeps its path, or the media URL would break.
    const withVersion = await load("http://localhost:8080/api/v2")
    expect(withVersion.apiOrigin()).toBe("http://localhost:8080")

    const noVersion = await load("http://localhost:8080")
    expect(noVersion.apiOrigin()).toBe("http://localhost:8080")

    const nested = await load("https://api.example.com/backend/api/v1")
    expect(nested.apiOrigin()).toBe("https://api.example.com/backend")
  })

  it("keeps an absolute URL untouched, for an object-store adapter", async () => {
    const { mediaUrl } = await load("http://localhost:8080/api/v1")
    expect(mediaUrl("https://cdn.example.com/p.jpg")).toBe("https://cdn.example.com/p.jpg")
    expect(mediaUrl("data:image/png;base64,AAA")).toBe("data:image/png;base64,AAA")
    expect(mediaUrl("blob:http://localhost/123")).toBe("blob:http://localhost/123")
  })

  it("accepts a key without a leading slash and returns null for empty", async () => {
    const { mediaUrl } = await load("http://localhost:8080/api/v1")
    expect(mediaUrl("media/products/x.png")).toBe("http://localhost:8080/media/products/x.png")
    expect(mediaUrl(null)).toBeNull()
    expect(mediaUrl(undefined)).toBeNull()
    expect(mediaUrl("")).toBeNull()
  })

  it("uses the default API base when none is configured", async () => {
    const { mediaUrl, DEFAULT_API_BASE_URL } = await load()
    expect(mediaUrl("/media/products/a.webp")).toBe(
      `${DEFAULT_API_BASE_URL.replace("/api/v1", "")}/media/products/a.webp`,
    )
  })
})

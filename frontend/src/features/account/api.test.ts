import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { API_BASE_URL } from "@/lib/api/client"
import { tokenStore } from "@/lib/api/token"
import {
  createAddress,
  deleteAddress,
  getProfile,
  listAddresses,
  updateAddress,
  updateProfile,
} from "./api"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

type FetchCall = [input: string, init?: RequestInit]

interface FetchMock {
  (input: string, init?: RequestInit): Promise<Response>
  mock: { calls: FetchCall[] }
  mockResolvedValueOnce(value: Response): FetchMock
}

function stubFetch(...responses: Response[]): FetchMock {
  const mock = vi.fn() as unknown as FetchMock
  for (const response of responses) mock.mockResolvedValueOnce(response)
  vi.stubGlobal("fetch", mock)
  return mock
}

const addressPayload = {
  data: {
    id: "a1",
    recipientName: "An Nguyen",
    phone: "0901234567",
    addressLine1: "12 Le Loi",
    addressLine2: null,
    ward: "Ben Thanh",
    district: "District 1",
    cityOrProvince: "Ho Chi Minh City",
    postalCode: "700000",
    isDefault: true,
  },
  message: "Success",
}

describe("account api", () => {
  beforeEach(() => {
    tokenStore.set("access-token")
  })

  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it("reads the profile with the access token", async () => {
    const mock = stubFetch(
      jsonResponse({ data: { id: "u1", email: "a@b.c" }, message: "Success" }),
    )

    await getProfile()

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/account/profile`)
    expect(init?.method).toBe("GET")
    expect((init?.headers as Record<string, string>).Authorization).toBe(
      "Bearer access-token",
    )
  })

  it("patches the profile", async () => {
    const mock = stubFetch(
      jsonResponse({ data: { id: "u1", email: "a@b.c" }, message: "Success" }),
    )

    await updateProfile({ firstName: "An", phone: "0901234567" })

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/account/profile`)
    expect(init?.method).toBe("PATCH")
    expect(JSON.parse(String(init?.body))).toEqual({
      firstName: "An",
      phone: "0901234567",
    })
  })

  it("lists the addresses", async () => {
    const mock = stubFetch(jsonResponse({ data: [addressPayload.data], message: "Success" }))

    const addresses = await listAddresses()

    expect(addresses).toHaveLength(1)
    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/account/addresses`)
  })

  it("creates an address", async () => {
    const mock = stubFetch(jsonResponse(addressPayload, 201))

    const created = await createAddress({
      recipientName: "An Nguyen",
      addressLine1: "12 Le Loi",
      cityOrProvince: "Ho Chi Minh City",
      isDefault: true,
    })

    expect(created.isDefault).toBe(true)
    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/account/addresses`)
    expect(init?.method).toBe("POST")
  })

  it("patches one address", async () => {
    const mock = stubFetch(jsonResponse(addressPayload))

    await updateAddress("a1", { recipientName: "An Nguyet" })

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/account/addresses/a1`)
    expect(init?.method).toBe("PATCH")
  })

  it("deletes one address", async () => {
    const mock = stubFetch(new Response(null, { status: 204 }))

    await deleteAddress("a1")

    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/account/addresses/a1`)
    expect(init?.method).toBe("DELETE")
  })
})

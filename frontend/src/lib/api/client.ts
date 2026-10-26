import { tokenStore } from "./token"
import type {
  ApiEnvelope,
  ApiErrorBody,
  ApiErrorCode,
  Paginated,
  PageEnvelope,
} from "./types"
import type { AuthResponse } from "@/types/auth"

export const DEFAULT_API_BASE_URL = "http://localhost:8080/api/v1"

function resolveBaseUrl(): string {
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL?.trim()
  const base = configured && configured.length > 0 ? configured : DEFAULT_API_BASE_URL
  return base.replace(/\/+$/, "")
}

/** Base URL for every backend call. Only this module decides where the API lives. */
export const API_BASE_URL = resolveBaseUrl()

export interface ApiErrorOptions {
  status: number
  code: ApiErrorCode
  fieldErrors?: Record<string, string>
  path?: string
  cause?: unknown
}

/** Normalized error thrown by the API client for every failed request. */
export class ApiError extends Error {
  readonly status: number
  readonly code: ApiErrorCode
  readonly fieldErrors?: Record<string, string>
  readonly path?: string
  override readonly cause?: unknown

  constructor(message: string, options: ApiErrorOptions) {
    super(message)
    this.name = "ApiError"
    this.status = options.status
    this.code = options.code
    this.fieldErrors = options.fieldErrors
    this.path = options.path
    this.cause = options.cause
  }

  get isUnauthenticated(): boolean {
    return this.status === 401
  }

  get isNetworkError(): boolean {
    return this.code === "NETWORK_ERROR"
  }
}

export type QueryValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | ReadonlyArray<string | number | boolean>

export type QueryParams = Record<string, QueryValue>

export interface ApiRequestOptions extends Omit<RequestInit, "body" | "headers"> {
  /** JSON body unless it is `FormData`, which is sent as multipart. */
  body?: unknown
  headers?: Record<string, string>
  query?: QueryParams
  /** Overrides the in-memory access token (auth flows only). */
  token?: string | null
  /** Do not attach the access token or attempt a refresh. */
  skipAuth?: boolean
  /** Do not attempt the 401 → refresh → retry flow. */
  skipRefresh?: boolean
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function statusToCode(status: number): ApiErrorCode {
  switch (status) {
    case 400:
      return "VALIDATION_ERROR"
    case 401:
      return "UNAUTHENTICATED"
    case 403:
      return "FORBIDDEN"
    case 404:
      return "NOT_FOUND"
    case 409:
      return "CONFLICT"
    case 429:
      return "RATE_LIMITED"
    default:
      return status >= 500 ? "INTERNAL_ERROR" : "HTTP_ERROR"
  }
}

function queryString(query?: QueryParams): string {
  if (!query) return ""
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue
    if (Array.isArray(value)) {
      for (const item of value) params.append(key, String(item))
      continue
    }
    params.set(key, String(value))
  }
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ""
}

export function buildUrl(path: string, query?: QueryParams): string {
  const base = /^https?:\/\//i.test(path)
    ? path
    : `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`
  const serialized = queryString(query)
  if (!serialized) return base
  return `${base}${base.includes("?") ? "&" : "?"}${serialized.slice(1)}`
}

/**
 * Origin the backend serves static media from (`/media/**` on the API host,
 * without the `/api/v1` prefix).
 *
 * Only the API *base path* (`/api/v1`) is stripped, never a last segment, so a
 * base without the version (e.g. `http://localhost:8080`) keeps its path.
 */
export function apiOrigin(): string {
  return API_BASE_URL.replace(/\/api(\/v\d+)?$/i, "")
}

/**
 * Turns a stored image path into a URL the browser can load.
 *
 * <p>The API returns storage keys as root-relative paths (`/media/products/…`).
 * Rendered as-is, the browser resolves them against the *frontend* origin, which
 * has no such files — the image 404s. Absolute URLs and data URIs are returned
 * unchanged so an object-store adapter needs no client change.
 */
export function mediaUrl(path: string | null | undefined): string | null {
  if (!path) return null
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  return `${apiOrigin()}${path.startsWith("/") ? "" : "/"}${path}`
}

/**
 * True when the target lives on the API origin. The access token is only ever
 * attached to the API, so an absolute URL can never leak it elsewhere.
 */
function isSameOrigin(url: string): boolean {
  try {
    return new URL(url).origin === new URL(API_BASE_URL).origin
  } catch {
    return false
  }
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    return undefined
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const payload = await readJson(response)
  if (isRecord(payload) && typeof payload.code === "string") {
    const fieldErrors = isRecord(payload.fieldErrors)
      ? Object.fromEntries(
          Object.entries(payload.fieldErrors).filter(
            (entry): entry is [string, string] => typeof entry[1] === "string",
          ),
        )
      : undefined
    return new ApiError(
      typeof payload.message === "string" && payload.message.length > 0
        ? payload.message
        : "Request failed",
      {
        status: response.status,
        code: payload.code as ApiErrorCode,
        fieldErrors,
        path: typeof payload.path === "string" ? payload.path : undefined,
      },
    )
  }

  return new ApiError(
    response.statusText || `Request failed with status ${response.status}`,
    { status: response.status, code: statusToCode(response.status) },
  )
}

/**
 * Paths that must never trigger the refresh flow: a 401 from login/register
 * means bad credentials, and refreshing there would turn one failed attempt
 * into two requests. `/auth/logout` and `/auth/me` are excluded on purpose —
 * refreshing there revokes the stale refresh chain instead of leaking it.
 */
const NO_REFRESH_PATHS = new Set(["/auth/login", "/auth/register", "/auth/refresh"])

/** Minimal shape of the Web Locks API used to serialise the refresh. */
interface RefreshLockManager {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>
}

function refreshLocks(): RefreshLockManager | undefined {
  if (typeof navigator === "undefined") return undefined
  return (navigator as Navigator & { locks?: RefreshLockManager }).locks
}

/**
 * Serialises the refresh across tabs. The refresh token rotates on every use,
 * so two tabs refreshing at the same moment would present an already-rotated
 * token and the backend would treat it as theft.
 */
function withRefreshLock<T>(run: () => Promise<T>): Promise<T> {
  const locks = refreshLocks()
  if (!locks) return run()
  return locks.request("clothing-auth-refresh", run)
}

let refreshPromise: Promise<AuthResponse | null> | null = null

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. One refresh
 * runs at a time per tab, and the Web Locks API serialises it across tabs.
 * The token is only dropped when nobody else replaced it meanwhile, so a
 * failed refresh never discards a session established by a concurrent login.
 */
export async function refreshAccessToken(): Promise<AuthResponse | null> {
  if (refreshPromise) return refreshPromise

  const previousToken = tokenStore.get()

  const run = async (): Promise<AuthResponse | null> => {
    try {
      const response = await fetch(buildUrl("/auth/refresh"), {
        method: "POST",
        credentials: "include",
        headers: { Accept: "application/json" },
        cache: "no-store",
      })
      if (!response.ok) {
        if (tokenStore.get() === previousToken) tokenStore.clear()
        return null
      }
      const payload = await readJson(response)
      const data = isRecord(payload) && "data" in payload ? payload.data : payload
      if (!isRecord(data) || typeof data.accessToken !== "string") {
        if (tokenStore.get() === previousToken) tokenStore.clear()
        return null
      }
      const auth = data as unknown as AuthResponse
      tokenStore.set(auth.accessToken)
      return auth
    } catch {
      if (tokenStore.get() === previousToken) tokenStore.clear()
      return null
    }
  }

  refreshPromise = withRefreshLock(run).finally(() => {
    refreshPromise = null
  })

  return refreshPromise
}

async function send(
  path: string,
  options: ApiRequestOptions = {},
  retried = false,
): Promise<Response> {
  const {
    body,
    headers: headerOverrides,
    query,
    token,
    skipAuth = false,
    skipRefresh = false,
    ...init
  } = options

  const headers: Record<string, string> = {
    Accept: "application/json",
    ...headerOverrides,
  }
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData
  if (body !== undefined && !isFormData) {
    headers["Content-Type"] ??= "application/json"
  }

  // The in-memory token belongs to the browser session. Server Components never
  // read tokens (catalog is public), which also prevents cross-request leakage
  // through module state in the Node runtime.
  const storedToken = typeof window === "undefined" ? null : tokenStore.get()
  const accessToken = skipAuth ? null : (token ?? storedToken)
  const target = buildUrl(path, query)
  if (accessToken && isSameOrigin(target)) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  let response: Response
  try {
    response = await fetch(target, {
      cache: "no-store",
      ...init,
      headers,
      body: isFormData
        ? (body as FormData)
        : body === undefined
          ? undefined
          : JSON.stringify(body),
    })
  } catch (cause) {
    throw new ApiError(
      "Unable to reach the server. Check your connection and try again.",
      { status: 0, code: "NETWORK_ERROR", cause },
    )
  }

  const noRefresh = NO_REFRESH_PATHS.has(path)
  if (
    response.status === 401 &&
    !retried &&
    !skipAuth &&
    !skipRefresh &&
    !noRefresh &&
    typeof window !== "undefined"
  ) {
    const auth = await refreshAccessToken()
    if (auth) {
      return send(path, { ...options, token: auth.accessToken }, true)
    }
  }

  return response
}

/** Performs a request and unwraps the standard `{ data, message }` envelope. */
export async function request<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const response = await send(path, options)
  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T

  const payload = await readJson(response)
  if (payload === undefined) return undefined as T
  return (isRecord(payload) && "data" in payload
    ? payload.data
    : payload) as T
}

/** Performs a request and normalizes the paginated envelope. */
export async function requestPage<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<Paginated<T>> {
  const response = await send(path, options)
  if (!response.ok) throw await toApiError(response)

  const payload = await readJson(response)
  if (!isRecord(payload) || !Array.isArray(payload.data)) {
    throw new ApiError("Unexpected paginated response from the server.", {
      status: response.status,
      code: "HTTP_ERROR",
      path,
    })
  }

  const pageEnvelope = payload as unknown as PageEnvelope<T>
  return {
    items: pageEnvelope.data,
    page: pageEnvelope.page,
    size: pageEnvelope.size,
    totalElements: pageEnvelope.totalElements,
    totalPages: pageEnvelope.totalPages,
  }
}

export const api = {
  get: <T>(path: string, options?: ApiRequestOptions) =>
    request<T>(path, { ...options, method: "GET" }),
  getPage: <T>(path: string, options?: ApiRequestOptions) =>
    requestPage<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),
  patch: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    request<T>(path, { ...options, method: "PATCH", body }),
  put: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    request<T>(path, { ...options, method: "PUT", body }),
  delete: <T = void>(path: string, options?: ApiRequestOptions) =>
    request<T>(path, { ...options, method: "DELETE" }),
}

export type { ApiEnvelope, ApiErrorBody, PageEnvelope }

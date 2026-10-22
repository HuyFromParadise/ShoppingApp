/** Stable error codes emitted by the backend (`ErrorCode` enum). */
export type BackendErrorCode =
  | "VALIDATION_ERROR"
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "OUT_OF_STOCK"
  | "INVALID_STATE_TRANSITION"
  | "IDEMPOTENCY_KEY_REUSED"
  | "RATE_LIMITED"
  | "PAYMENT_FAILED"
  | "PROVIDER_NOT_CONFIGURED"
  | "INTERNAL_ERROR"

/** Client-side codes for failures that never reach the backend. */
export type ClientErrorCode = "NETWORK_ERROR" | "HTTP_ERROR"

export type ApiErrorCode = BackendErrorCode | ClientErrorCode

/** Standard success envelope: `{ data, message }`. */
export interface ApiEnvelope<T> {
  data: T
  message: string
}

/** Paginated success envelope used by list endpoints. */
export interface PageEnvelope<T> {
  data: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  message: string
}

/** Normalized page shape returned by `api.getPage`. */
export interface Paginated<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Error envelope: `{ timestamp, status, code, message, fieldErrors?, path }`. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  code: ApiErrorCode
  message: string
  fieldErrors?: Record<string, string>
  path?: string
}

export interface Money {
  minorUnits: number
  currency: string
}

/** Shared auth DTOs mirroring the backend auth responses. */

export type UserRole = "CUSTOMER" | "ADMIN"

export type UserStatus = "ACTIVE" | "LOCKED" | "DISABLED"

export interface UserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  role: UserRole
  status: UserStatus
}

/**
 * Login/register/refresh response. The refresh token never appears here — it
 * travels only as an HttpOnly cookie scoped to `/api/v1/auth`.
 */
export interface AuthResponse {
  accessToken: string
  user: UserResponse
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone?: string
}

import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from "@/types/auth"

/**
 * Auth endpoints. Every call carries `credentials: "include"` so the HttpOnly
 * refresh cookie is sent and stored; the access token itself only ever lives
 * in the in-memory store.
 *
 * Refreshing lives in `lib/api/client.ts` instead: it has to share one
 * single-flight lock with the 401 → refresh → retry path, otherwise the
 * rotating refresh token would be presented twice.
 */

export function login(input: LoginRequest): Promise<AuthResponse> {
  return api.post<AuthResponse>(ENDPOINTS.auth.login, input, {
    credentials: "include",
    skipAuth: true,
    skipRefresh: true,
  })
}

export function register(input: RegisterRequest): Promise<AuthResponse> {
  return api.post<AuthResponse>(ENDPOINTS.auth.register, input, {
    credentials: "include",
    skipAuth: true,
    skipRefresh: true,
  })
}

/** Revokes the presented refresh token and its descendants. */
export function logout(): Promise<void> {
  return api.post<void>(ENDPOINTS.auth.logout, undefined, {
    credentials: "include",
  })
}

export function fetchCurrentUser(): Promise<UserResponse> {
  return api.get<UserResponse>(ENDPOINTS.auth.me)
}

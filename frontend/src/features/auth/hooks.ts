"use client"

import { useMutation } from "@tanstack/react-query"
import type { LoginRequest, RegisterRequest } from "@/types/auth"
import { login, logout, register } from "./api"
import { sessionStore } from "./session"

/**
 * Auth mutations. Login and register establish the session on success; logout
 * always clears the local session, even when the network call fails.
 */

export function useLogin() {
  return useMutation({
    mutationFn: (input: LoginRequest) => login(input),
    onSuccess: (auth) => sessionStore.establish(auth),
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: (input: RegisterRequest) => register(input),
    onSuccess: (auth) => sessionStore.establish(auth),
  })
}

export function useLogout() {
  return useMutation({
    mutationFn: async () => {
      try {
        await logout()
      } finally {
        sessionStore.clear()
      }
    },
  })
}

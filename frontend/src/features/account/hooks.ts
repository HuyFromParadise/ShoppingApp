"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { sessionStore } from "@/features/auth/session"
import {
  createAddress,
  deleteAddress,
  getProfile,
  listAddresses,
  updateAddress,
  updateProfile,
} from "./api"
import type { AddressInput, AddressPatch, ProfilePatch } from "@/types/account"

/**
 * Account queries and mutations. Authenticated data is fetched client-side so
 * the API client can refresh the access token in one place.
 */

export const ACCOUNT_KEYS = {
  profile: ["account", "profile"] as const,
  addresses: ["account", "addresses"] as const,
}

export function useProfile() {
  return useQuery({
    queryKey: ACCOUNT_KEYS.profile,
    queryFn: getProfile,
  })
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (patch: ProfilePatch) => updateProfile(patch),
    onSuccess: (user) => {
      queryClient.setQueryData(ACCOUNT_KEYS.profile, user)
      // The header shows the user's name, so the session follows the profile.
      sessionStore.updateUser(user)
    },
  })
}

export function useAddresses() {
  return useQuery({
    queryKey: ACCOUNT_KEYS.addresses,
    queryFn: listAddresses,
  })
}

export function useCreateAddress() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AddressInput) => createAddress(input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ACCOUNT_KEYS.addresses }),
  })
}

export function useUpdateAddress() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      addressId,
      patch,
    }: {
      addressId: string
      patch: AddressPatch
    }) => updateAddress(addressId, patch),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ACCOUNT_KEYS.addresses }),
  })
}

export function useDeleteAddress() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (addressId: string) => deleteAddress(addressId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ACCOUNT_KEYS.addresses }),
  })
}

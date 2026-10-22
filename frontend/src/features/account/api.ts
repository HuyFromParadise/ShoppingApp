import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type {
  Address,
  AddressInput,
  AddressPatch,
  ProfilePatch,
} from "@/types/account"
import type { UserResponse } from "@/types/auth"

/**
 * Account endpoints. They are authenticated, so the API client attaches the
 * in-memory access token and refreshes it when it expired.
 */

export function getProfile(): Promise<UserResponse> {
  return api.get<UserResponse>(ENDPOINTS.account.profile)
}

export function updateProfile(patch: ProfilePatch): Promise<UserResponse> {
  return api.patch<UserResponse>(ENDPOINTS.account.profile, patch)
}

export function listAddresses(): Promise<Address[]> {
  return api.get<Address[]>(ENDPOINTS.account.addresses)
}

export function createAddress(input: AddressInput): Promise<Address> {
  return api.post<Address>(ENDPOINTS.account.addresses, input)
}

export function updateAddress(
  addressId: string,
  patch: AddressPatch,
): Promise<Address> {
  return api.patch<Address>(ENDPOINTS.account.address(addressId), patch)
}

export function deleteAddress(addressId: string): Promise<void> {
  return api.delete<void>(ENDPOINTS.account.address(addressId))
}

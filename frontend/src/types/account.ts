/** Shared account DTOs mirroring the backend account responses. */

/** A shipping address of the signed-in customer. */
export interface Address {
  id: string
  recipientName: string
  phone: string | null
  addressLine1: string
  addressLine2: string | null
  ward: string | null
  district: string | null
  cityOrProvince: string
  postalCode: string | null
  isDefault: boolean
}

/** Payload for creating an address. */
export interface AddressInput {
  recipientName: string
  phone?: string
  addressLine1: string
  addressLine2?: string
  ward?: string
  district?: string
  cityOrProvince: string
  postalCode?: string
  isDefault?: boolean
}

/** Payload for updating an address; absent fields stay unchanged. */
export interface AddressPatch {
  recipientName?: string
  phone?: string
  addressLine1?: string
  addressLine2?: string
  ward?: string
  district?: string
  cityOrProvince?: string
  postalCode?: string
  isDefault?: boolean
}

/** Payload for updating the profile; absent fields stay unchanged. */
export interface ProfilePatch {
  firstName?: string
  lastName?: string
  phone?: string
}

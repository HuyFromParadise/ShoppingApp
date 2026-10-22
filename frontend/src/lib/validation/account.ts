import { z } from "zod"

/**
 * Account form contracts mirroring the backend DTO constraints
 * (DESIGN_REFERENCE §5.4 and the `addresses` columns in §4).
 */

export const profileSchema = z.object({
  firstName: z
    .string()
    .trim()
    .min(1, "First name is required")
    .max(100, "First name must be at most 100 characters"),
  lastName: z
    .string()
    .trim()
    .min(1, "Last name is required")
    .max(100, "Last name must be at most 100 characters"),
  phone: z
    .string()
    .trim()
    .max(30, "Phone number must be at most 30 characters"),
})

export type ProfileValues = z.infer<typeof profileSchema>

export const addressSchema = z.object({
  recipientName: z
    .string()
    .trim()
    .min(1, "Recipient name is required")
    .max(100, "Recipient name must be at most 100 characters"),
  phone: z
    .string()
    .trim()
    .max(30, "Phone number must be at most 30 characters"),
  addressLine1: z
    .string()
    .trim()
    .min(1, "Address line 1 is required")
    .max(255, "Address line 1 must be at most 255 characters"),
  addressLine2: z
    .string()
    .trim()
    .max(255, "Address line 2 must be at most 255 characters"),
  ward: z.string().trim().max(100, "Ward must be at most 100 characters"),
  district: z.string().trim().max(100, "District must be at most 100 characters"),
  cityOrProvince: z
    .string()
    .trim()
    .min(1, "City or province is required")
    .max(100, "City or province must be at most 100 characters"),
  postalCode: z
    .string()
    .trim()
    .max(20, "Postal code must be at most 20 characters"),
  isDefault: z.boolean(),
})

export type AddressValues = z.infer<typeof addressSchema>

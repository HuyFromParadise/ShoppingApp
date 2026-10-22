import { z } from "zod"

/**
 * Auth form contracts mirroring the backend DTO constraints
 * (DESIGN_REFERENCE §5.4). The common-password denylist stays server-side;
 * its rejection arrives as a `password` field error and is rendered as-is.
 */

const emailSchema = z
  .string()
  .trim()
  .min(1, "Email is required")
  .email("Must be a valid email address")
  .max(255, "Email must be at most 255 characters")

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Password is required"),
})

export type LoginValues = z.infer<typeof loginSchema>

export const registerSchema = z
  .object({
    email: emailSchema,
    password: z
      .string()
      .min(10, "Password must be at least 10 characters")
      .max(72, "Password must be at most 72 characters"),
    confirmPassword: z.string().min(1, "Please confirm your password"),
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
      .max(30, "Phone number must be at most 30 characters")
      .optional(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  })

export type RegisterValues = z.infer<typeof registerSchema>

/** Normalizes an email the same way the backend does before storing it. */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}

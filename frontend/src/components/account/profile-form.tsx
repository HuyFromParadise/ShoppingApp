"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import * as React from "react"
import { useForm } from "react-hook-form"
import { Button } from "@/components/ui/button"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import { useToast } from "@/components/ui/toast"
import { useUpdateProfile } from "@/features/account/hooks"
import { applyServerError } from "@/lib/api/form-errors"
import { profileSchema, type ProfileValues } from "@/lib/validation/account"
import type { UserResponse } from "@/types/auth"

/**
 * Editable contact details of the signed-in customer. The email is the login
 * identity and is shown read-only.
 */
export function ProfileForm({ profile }: { profile: UserResponse }) {
  const updateProfile = useUpdateProfile()
  const { toast } = useToast()
  const [formError, setFormError] = React.useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    values: {
      firstName: profile.firstName,
      lastName: profile.lastName,
      phone: profile.phone ?? "",
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await updateProfile.mutateAsync({
        firstName: values.firstName,
        lastName: values.lastName,
        phone: values.phone,
      })
      toast({ title: "Profile updated", variant: "success" })
    } catch (error) {
      setFormError(
        applyServerError(error, setError, ["firstName", "lastName", "phone"]),
      )
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="max-w-md space-y-5">
      <div className="space-y-1.5">
        <span className="block text-sm font-medium text-ink">Email</span>
        <p className="text-sm text-muted">{profile.email}</p>
        <p className="text-xs text-muted">
          The email is your login and cannot be changed here.
        </p>
      </div>

      <FormField
        id="firstName"
        label="First name"
        error={errors.firstName?.message}
        required
      >
        <Input autoComplete="given-name" {...register("firstName")} />
      </FormField>

      <FormField
        id="lastName"
        label="Last name"
        error={errors.lastName?.message}
        required
      >
        <Input autoComplete="family-name" {...register("lastName")} />
      </FormField>

      <FormField
        id="phone"
        label="Phone"
        hint="Optional — used for delivery updates only."
        error={errors.phone?.message}
      >
        <Input type="tel" autoComplete="tel" {...register("phone")} />
      </FormField>

      {formError ? (
        <p role="alert" className="text-sm text-accent">
          {formError}
        </p>
      ) : null}

      <Button type="submit" loading={isSubmitting}>
        Save changes
      </Button>
    </form>
  )
}

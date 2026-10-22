"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import * as React from "react"
import { useForm } from "react-hook-form"
import { Button } from "@/components/ui/button"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import {
  useCreateAddress,
  useUpdateAddress,
} from "@/features/account/hooks"
import { applyServerError } from "@/lib/api/form-errors"
import { addressSchema, type AddressValues } from "@/lib/validation/account"
import type { Address } from "@/types/account"

const ADDRESS_FIELDS = [
  "recipientName",
  "phone",
  "addressLine1",
  "addressLine2",
  "ward",
  "district",
  "cityOrProvince",
  "postalCode",
] as const

/**
 * Create or edit a shipping address. Used for both: without an address it
 * creates one, with an address it patches that one.
 */
export function AddressForm({
  address,
  onSaved,
}: {
  address?: Address
  onSaved?: () => void
}) {
  const createAddress = useCreateAddress()
  const updateAddress = useUpdateAddress()
  const [formError, setFormError] = React.useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AddressValues>({
    resolver: zodResolver(addressSchema),
    values: {
      recipientName: address?.recipientName ?? "",
      phone: address?.phone ?? "",
      addressLine1: address?.addressLine1 ?? "",
      addressLine2: address?.addressLine2 ?? "",
      ward: address?.ward ?? "",
      district: address?.district ?? "",
      cityOrProvince: address?.cityOrProvince ?? "",
      postalCode: address?.postalCode ?? "",
      isDefault: address?.isDefault ?? false,
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    const payload = {
      recipientName: values.recipientName,
      phone: values.phone,
      addressLine1: values.addressLine1,
      addressLine2: values.addressLine2,
      ward: values.ward,
      district: values.district,
      cityOrProvince: values.cityOrProvince,
      postalCode: values.postalCode,
      isDefault: values.isDefault,
    }
    try {
      if (address) {
        await updateAddress.mutateAsync({ addressId: address.id, patch: payload })
      } else {
        await createAddress.mutateAsync(payload)
      }
      onSaved?.()
    } catch (error) {
      setFormError(applyServerError(error, setError, [...ADDRESS_FIELDS]))
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <FormField
        id="recipientName"
        label="Recipient name"
        error={errors.recipientName?.message}
        required
      >
        <Input autoComplete="name" {...register("recipientName")} />
      </FormField>

      <FormField id="phone" label="Phone" error={errors.phone?.message}>
        <Input type="tel" autoComplete="tel" {...register("phone")} />
      </FormField>

      <FormField
        id="addressLine1"
        label="Address line 1"
        error={errors.addressLine1?.message}
        required
      >
        <Input autoComplete="address-line1" {...register("addressLine1")} />
      </FormField>

      <FormField
        id="addressLine2"
        label="Address line 2"
        hint="Optional — apartment, floor, etc."
        error={errors.addressLine2?.message}
      >
        <Input autoComplete="address-line2" {...register("addressLine2")} />
      </FormField>

      <div className="grid gap-5 sm:grid-cols-2">
        <FormField
          id="ward"
          label="Ward"
          error={errors.ward?.message}
        >
          <Input autoComplete="address-level3" {...register("ward")} />
        </FormField>

        <FormField
          id="district"
          label="District"
          error={errors.district?.message}
        >
          <Input autoComplete="address-level2" {...register("district")} />
        </FormField>
      </div>

      <FormField
        id="cityOrProvince"
        label="City or province"
        error={errors.cityOrProvince?.message}
        required
      >
        <Input autoComplete="address-level1" {...register("cityOrProvince")} />
      </FormField>

      <FormField
        id="postalCode"
        label="Postal code"
        error={errors.postalCode?.message}
      >
        <Input autoComplete="postal-code" {...register("postalCode")} />
      </FormField>

      <label className="flex items-center gap-2 text-sm text-ink">
        <input
          type="checkbox"
          className="h-4 w-4 rounded border-line text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          {...register("isDefault")}
        />
        Use as my default address
      </label>

      {formError ? (
        <p role="alert" className="text-sm text-accent">
          {formError}
        </p>
      ) : null}

      <Button type="submit" loading={isSubmitting}>
        {address ? "Save address" : "Add address"}
      </Button>
    </form>
  )
}

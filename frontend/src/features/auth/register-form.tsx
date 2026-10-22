"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import * as React from "react"
import { useForm } from "react-hook-form"
import { Button } from "@/components/ui/button"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import {
  normalizeEmail,
  registerSchema,
  type RegisterValues,
} from "@/lib/validation/auth"
import { applyServerError } from "@/lib/api/form-errors"
import { useRegister } from "./hooks"

export function RegisterForm({ next }: { next?: string }) {
  const router = useRouter()
  const registerMutation = useRegister()
  const [formError, setFormError] = React.useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: "",
      password: "",
      confirmPassword: "",
      firstName: "",
      lastName: "",
      phone: "",
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await registerMutation.mutateAsync({
        email: normalizeEmail(values.email),
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        phone: values.phone ? values.phone : undefined,
      })
      router.push(next ?? "/")
    } catch (error) {
      setFormError(
        applyServerError(error, setError, [
          "email",
          "password",
          "firstName",
          "lastName",
          "phone",
        ]),
      )
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <FormField
        id="email"
        label="Email"
        error={errors.email?.message}
        required
      >
        <Input
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          {...register("email")}
        />
      </FormField>

      <div className="grid gap-5 sm:grid-cols-2">
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
      </div>

      <FormField
        id="phone"
        label="Phone"
        hint="Optional — used for delivery updates only."
        error={errors.phone?.message}
      >
        <Input type="tel" autoComplete="tel" {...register("phone")} />
      </FormField>

      <FormField
        id="password"
        label="Password"
        hint="Between 10 and 72 characters."
        error={errors.password?.message}
        required
      >
        <Input
          type="password"
          autoComplete="new-password"
          {...register("password")}
        />
      </FormField>

      <FormField
        id="confirmPassword"
        label="Confirm password"
        error={errors.confirmPassword?.message}
        required
      >
        <Input
          type="password"
          autoComplete="new-password"
          {...register("confirmPassword")}
        />
      </FormField>

      {formError ? (
        <p role="alert" className="text-sm text-accent">
          {formError}
        </p>
      ) : null}

      <Button type="submit" loading={isSubmitting} className="w-full">
        Create account
      </Button>

      <p className="text-sm text-muted">
        Already have an account?{" "}
        <Link
          href={next ? `/login?next=${encodeURIComponent(next)}` : "/login"}
          className="text-ink underline underline-offset-4 transition-colors duration-150 hover:text-accent"
        >
          Sign in
        </Link>
      </p>
    </form>
  )
}

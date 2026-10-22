"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import * as React from "react"
import { useForm } from "react-hook-form"
import { Button } from "@/components/ui/button"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import { loginSchema, normalizeEmail, type LoginValues } from "@/lib/validation/auth"
import { applyServerError } from "@/lib/api/form-errors"
import { useLogin } from "./hooks"

export function LoginForm({ next }: { next?: string }) {
  const router = useRouter()
  const loginMutation = useLogin()
  const [formError, setFormError] = React.useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await loginMutation.mutateAsync({
        email: normalizeEmail(values.email),
        password: values.password,
      })
      router.push(next ?? "/")
    } catch (error) {
      setFormError(applyServerError(error, setError, ["email", "password"]))
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

      <FormField
        id="password"
        label="Password"
        error={errors.password?.message}
        required
      >
        <Input
          type="password"
          autoComplete="current-password"
          {...register("password")}
        />
      </FormField>

      {formError ? (
        <p role="alert" className="text-sm text-accent">
          {formError}
        </p>
      ) : null}

      <Button type="submit" loading={isSubmitting} className="w-full">
        Sign in
      </Button>

      <p className="text-sm text-muted">
        New here?{" "}
        <Link
          href={next ? `/register?next=${encodeURIComponent(next)}` : "/register"}
          className="text-ink underline underline-offset-4 transition-colors duration-150 hover:text-accent"
        >
          Create an account
        </Link>
      </p>
    </form>
  )
}

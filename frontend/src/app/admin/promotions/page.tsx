"use client"

import * as React from "react"
import { DataTable, type Column } from "@/components/admin/data-table"
import { MoneyField, SelectField } from "@/components/admin/form-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { FormField } from "@/components/ui/form-field"
import { Input } from "@/components/ui/input"
import { useToast } from "@/components/ui/toast"
import {
  useCreatePromotion,
  usePromotions,
  useUpdatePromotion,
} from "@/features/admin/hooks"
import { ApiError } from "@/lib/api/client"
import { formatDate } from "@/lib/utils/date"
import {
  describePromotion,
  type AdminPromotion,
  type PromotionType,
} from "@/types/p1"

/**
 * Admin promotions (PROJECT_SPEC §6, §11: {@code /admin/promotions}).
 *
 * <p>A code is deactivated, never deleted, so past orders keep pointing at a
 * real promotion. The percentage cap and the all-conditions checks live on the
 * server; the form only prevents obvious mistakes.
 */
export default function AdminPromotionsPage() {
  const promotions = usePromotions()
  const [creating, setCreating] = React.useState(false)

  const columns: ReadonlyArray<Column<AdminPromotion>> = [
    {
      key: "code",
      header: "Code",
      render: (row) => (
        <div>
          <p className="font-mono text-sm font-medium text-ink">{row.code}</p>
          <p className="text-xs text-muted">{row.name ?? "—"}</p>
        </div>
      ),
    },
    {
      key: "value",
      header: "Discount",
      render: (row) => <span className="text-sm text-ink">{describePromotion(row)}</span>,
    },
    {
      key: "conditions",
      header: "Conditions",
      hideOnMobile: true,
      render: (row) => (
        <span className="text-xs text-muted">
          {row.minOrderMinor > 0
            ? `Min ${row.minOrderMinor.toLocaleString("vi-VN")}`
            : "No minimum"}
          {row.maxDiscountMinor
            ? ` · capped ${row.maxDiscountMinor.toLocaleString("vi-VN")}`
            : ""}
        </span>
      ),
    },
    {
      key: "window",
      header: "Window",
      hideOnMobile: true,
      render: (row) => (
        <span className="text-xs text-muted">
          {row.startsAt ? formatDate(row.startsAt) : "Always"}
          {" → "}
          {row.endsAt ? formatDate(row.endsAt) : "No end"}
        </span>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => (
        <Badge variant={row.active ? "success" : "neutral"}>
          {row.active ? "Active" : "Inactive"}
        </Badge>
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (row) => <PromotionToggle promotion={row} />,
    },
  ]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Promotions</h1>
          <p className="mt-1 text-xs text-muted">
            Codes are deactivated rather than deleted, so past orders stay
            explainable. Every condition is checked again at checkout.
          </p>
        </div>
        <Button onClick={() => setCreating((open) => !open)}>
          {creating ? "Close" : "New promotion"}
        </Button>
      </header>

      {creating ? <CreatePromotionForm onDone={() => setCreating(false)} /> : null}

      {promotions.error ? (
        <ErrorState
          title="We could not load the promotions"
          onRetry={() => void promotions.refetch()}
        />
      ) : (
        <DataTable
          caption="Promotions"
          columns={columns}
          rows={promotions.data ?? []}
          rowKey={(row) => row.id}
          loading={promotions.isPending}
          emptyMessage="No promotions yet."
        />
      )}
    </div>
  )
}

function CreatePromotionForm({ onDone }: { onDone: () => void }) {
  const create = useCreatePromotion()
  const { toast } = useToast()
  const [code, setCode] = React.useState("")
  const [name, setName] = React.useState("")
  const [type, setType] = React.useState<PromotionType>("PERCENT")
  const [value, setValue] = React.useState<number | null>(null)
  const [minOrder, setMinOrder] = React.useState<number | null>(null)
  const [maxDiscount, setMaxDiscount] = React.useState<number | null>(null)
  const [startsAt, setStartsAt] = React.useState("")
  const [endsAt, setEndsAt] = React.useState("")
  const [error, setError] = React.useState<string | null>(null)

  const valueInvalid =
    value === null || value <= 0 || (type === "PERCENT" && value > 100)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!code.trim()) {
      setError("A code is required.")
      return
    }
    if (valueInvalid) {
      setError(
        type === "PERCENT"
          ? "A percentage must be between 1 and 100."
          : "Enter a discount greater than zero.",
      )
      return
    }
    setError(null)
    create.mutate(
      {
        code: code.trim(),
        name: name.trim() || undefined,
        type,
        value: value as number,
        minOrder: minOrder ?? undefined,
        maxDiscount: maxDiscount ?? undefined,
        startsAt: startsAt ? new Date(startsAt).toISOString() : undefined,
        endsAt: endsAt ? new Date(endsAt).toISOString() : undefined,
        active: true,
      },
      {
        onSuccess: (promotion) => {
          toast({
            title: "Promotion created",
            description: promotion.code,
            variant: "success",
          })
          onDone()
        },
        onError: (failure) => {
          if (failure instanceof ApiError && failure.fieldErrors) {
            setError(Object.values(failure.fieldErrors)[0] ?? failure.message)
          } else {
            setError(failure instanceof Error ? failure.message : "Could not create it.")
          }
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4 border border-line bg-surface p-5">
      <h2 className="text-xs font-medium uppercase tracking-label text-muted">
        New promotion
      </h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="promo-code" label="Code" required hint="Letters, digits, _ and - only.">
          <Input
            value={code}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
            maxLength={50}
            autoComplete="off"
            placeholder="SAVE10"
          />
        </FormField>
        <FormField id="promo-name" label="Name">
          <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={200} />
        </FormField>
        <SelectField
          id="promo-type"
          label="Type"
          value={type}
          onChange={(event) => setType(event.target.value as PromotionType)}
          options={[
            { value: "PERCENT", label: "Percentage off" },
            { value: "FIXED", label: "Fixed amount off" },
          ]}
          hint="A percentage is a whole number, 1–100."
        />
        <MoneyField
          label={type === "PERCENT" ? "Percentage" : "Amount off"}
          currency={type === "PERCENT" ? "%" : "VND"}
          valueMinor={value}
          onChangeMinor={setValue}
          error={
            value !== null && valueInvalid
              ? type === "PERCENT"
                ? "Must be between 1 and 100."
                : "Must be greater than zero."
              : undefined
          }
        />
        <MoneyField
          label="Minimum order"
          currency="VND"
          valueMinor={minOrder}
          onChangeMinor={setMinOrder}
          hint="Leave empty for no minimum."
        />
        <MoneyField
          label="Maximum discount"
          currency="VND"
          valueMinor={maxDiscount}
          onChangeMinor={setMaxDiscount}
          hint="Optional cap, useful for percentages."
        />
        <label className="space-y-1.5 text-sm">
          <span className="block font-medium text-ink">Starts</span>
          <Input
            type="datetime-local"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
          />
        </label>
        <label className="space-y-1.5 text-sm">
          <span className="block font-medium text-ink">Ends</span>
          <Input
            type="datetime-local"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </label>
      </div>
      {error ? (
        <p role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
      <Button type="submit" loading={create.isPending}>
        Create promotion
      </Button>
    </form>
  )
}

function PromotionToggle({ promotion }: { promotion: AdminPromotion }) {
  const update = useUpdatePromotion()
  const { toast } = useToast()
  const [error, setError] = React.useState<string | null>(null)

  return (
    <div className="space-y-1">
      <button
        type="button"
        className="text-xs text-ink underline underline-offset-4 hover:text-accent disabled:opacity-50"
        disabled={update.isPending}
        onClick={() => {
          setError(null)
          update.mutate(
            { promotionId: promotion.id, input: { active: !promotion.active } },
            {
              onSuccess: (saved) =>
                toast({
                  title: saved.active ? "Promotion active" : "Promotion deactivated",
                  description: saved.active
                    ? "Customers can use this code again."
                    : "The code is refused at checkout; past orders are unaffected.",
                  variant: "success",
                }),
              onError: (failure) =>
                setError(failure instanceof Error ? failure.message : "Could not update it."),
            },
          )
        }}
      >
        {promotion.active ? "Deactivate" : "Activate"}
      </button>
      {error ? (
        <p role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
    </div>
  )
}

"use client"

import * as React from "react"
import { AddressForm } from "@/components/account/address-form"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog } from "@/components/ui/dialog"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { Skeleton } from "@/components/ui/skeleton"
import { useToast } from "@/components/ui/toast"
import {
  useAddresses,
  useDeleteAddress,
  useUpdateAddress,
} from "@/features/account/hooks"
import type { Address } from "@/types/account"

export default function AddressesPage() {
  const { data: addresses, isPending, error, refetch } = useAddresses()
  const deleteAddress = useDeleteAddress()
  const updateAddress = useUpdateAddress()
  const { toast } = useToast()
  const [creating, setCreating] = React.useState(false)
  const [editing, setEditing] = React.useState<Address | null>(null)

  const close = () => {
    setCreating(false)
    setEditing(null)
  }

  if (isPending) {
    return <Skeleton lines={5} />
  }

  if (error) {
    return (
      <ErrorState
        title="We could not load your addresses"
        onRetry={() => void refetch()}
      />
    )
  }

  const list = addresses ?? []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-lg font-medium tracking-tight text-ink">
          Shipping addresses
        </h2>
        <Button onClick={() => setCreating(true)}>Add address</Button>
      </div>

      {list.length === 0 ? (
        <EmptyState
          title="No addresses yet"
          description="Add a delivery address so we know where to send your order."
          action={<Button onClick={() => setCreating(true)}>Add address</Button>}
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {list.map((address) => (
            <li
              key={address.id}
              className="flex flex-col justify-between gap-4 rounded border border-line bg-surface p-5"
            >
              <div>
                <div className="flex items-center gap-2">
                  <p className="font-medium text-ink">{address.recipientName}</p>
                  {address.isDefault ? (
                    <Badge variant="accent">Default</Badge>
                  ) : null}
                </div>
                <address className="mt-2 text-sm not-italic text-muted">
                  {address.addressLine1}
                  {address.addressLine2 ? <>, {address.addressLine2}</> : null}
                  <br />
                  {address.ward ? <>{address.ward}, </> : null}
                  {address.district ? <>{address.district}, </> : null}
                  {address.cityOrProvince}
                  {address.postalCode ? <>, {address.postalCode}</> : null}
                  {address.phone ? (
                    <>
                      <br />
                      {address.phone}
                    </>
                  ) : null}
                </address>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setEditing(address)}
                >
                  Edit
                </Button>
                {!address.isDefault ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={updateAddress.isPending}
                    onClick={() =>
                      updateAddress.mutate(
                        { addressId: address.id, patch: { isDefault: true } },
                        {
                          onError: () =>
                            toast({
                              title: "We could not set the default address",
                              variant: "error",
                            }),
                        },
                      )
                    }
                  >
                    Make default
                  </Button>
                ) : null}
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={deleteAddress.isPending}
                  onClick={() => {
                    if (window.confirm("Delete this address?")) {
                      deleteAddress.mutate(address.id, {
                        onError: () =>
                          toast({
                            title: "We could not delete this address",
                            variant: "error",
                          }),
                      })
                    }
                  }}
                >
                  Delete
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Dialog
        open={creating || editing !== null}
        onOpenChange={(open) => {
          if (!open) close()
        }}
        title={editing ? "Edit address" : "New address"}
        description={
          editing
            ? "Update the delivery details of this address."
            : "Where should we deliver your order?"
        }
      >
        <AddressForm
          address={editing ?? undefined}
          onSaved={close}
        />
      </Dialog>
    </div>
  )
}

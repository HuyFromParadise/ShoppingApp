"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { useToast } from "@/components/ui/toast"
import { useAddCartItem } from "@/features/cart/hooks"
import { ApiError } from "@/lib/api/client"
import { MAX_LINE_QUANTITY } from "@/types/cart"
import { QuantityStepper } from "./quantity-stepper"

export interface AddToCartProps {
  variantId: string
  stockQuantity: number
}

/**
 * Quantity + submit for one variant. The backend re-validates the quantity and
 * the stock, so the failures surface as a toast with the server's own message.
 */
export function AddToCart({ variantId, stockQuantity }: AddToCartProps) {
  const addItem = useAddCartItem()
  const { toast } = useToast()
  const [quantity, setQuantity] = React.useState(1)

  // A newly selected variant starts from one again.
  React.useEffect(() => {
    setQuantity(1)
  }, [variantId])

  const soldOut = stockQuantity < 1
  const max = Math.min(MAX_LINE_QUANTITY, stockQuantity)

  function submit() {
    addItem.mutate(
      { variantId, quantity },
      {
        onSuccess: () =>
          toast({ title: "Added to your cart", variant: "success" }),
        onError: (error) => {
          const description =
            error instanceof ApiError
              ? error.status === 401
                ? "Sign in to add items to your cart."
                : error.message
              : "We could not add this item. Please try again."
          toast({
            title: "Could not add to cart",
            description,
            variant: "error",
          })
        },
      },
    )
  }

  return (
    <div className="mt-6 space-y-3">
      <QuantityStepper
        value={quantity}
        max={max}
        onChange={setQuantity}
        disabled={soldOut || addItem.isPending}
      />
      <Button
        size="lg"
        className="w-full"
        onClick={submit}
        loading={addItem.isPending}
        disabled={soldOut}
      >
        {soldOut ? "Out of stock" : "Add to cart"}
      </Button>
    </div>
  )
}

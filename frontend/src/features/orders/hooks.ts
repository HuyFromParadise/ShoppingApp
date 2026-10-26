"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CART_KEYS } from "@/features/cart/hooks"
import {
  cancelOrder,
  confirmMockPayment,
  createOrder,
  createQuote,
  getOrder,
  listOrders,
  listPayments,
  requestOrderReturn,
  startMockPayment,
} from "./api"
import type { CreateOrderInput, OrderDetail, QuoteInput } from "@/types/orders"

/**
 * Order workflow queries and mutations. Authenticated data is fetched
 * client-side so token refresh happens in one place. Every mutation
 * invalidates the server-backed views instead of patching a local copy, so a
 * cancelled order or a paid one is re-read rather than assumed.
 */

export const ORDER_KEYS = {
  all: ["orders"] as const,
  list: (page: number, size: number) => ["orders", "list", page, size] as const,
  detail: (orderId: string) => ["orders", "detail", orderId] as const,
  quote: (input: QuoteInput | null) =>
    [
      "checkout",
      "quote",
      input?.addressId ?? null,
      input?.shippingMethod ?? null,
      input?.promotionCode ?? null,
    ] as const,
  payments: (orderId: string) => ["orders", "payments", orderId] as const,
}

/** The order history page. */
export function useOrders(page: number, size: number) {
  return useQuery({
    queryKey: ORDER_KEYS.list(page, size),
    queryFn: () => listOrders(page, size),
  })
}

/** One order with its history. `undefined` while there is no id yet. */
export function useOrder(orderId: string | undefined) {
  return useQuery({
    queryKey: ORDER_KEYS.detail(orderId ?? ""),
    queryFn: () => getOrder(orderId as string),
    enabled: Boolean(orderId),
  })
}

/**
 * The server-authoritative quote. It is only requested once an address and a
 * shipping option are chosen, exactly the two inputs the endpoint needs.
 */
export function useQuote(input: QuoteInput | null) {
  return useQuery({
    queryKey: ORDER_KEYS.quote(input),
    queryFn: () => createQuote(input as QuoteInput),
    enabled: Boolean(input?.addressId && input.shippingMethod),
    // A quote is a moment in time: never serve a cached total.
    staleTime: 0,
    gcTime: 0,
    retry: false,
  })
}

export interface PlaceOrderVariables {
  input: CreateOrderInput
  /** Reused across retries of the same attempt, so a retry replays one order. */
  idempotencyKey: string
}

export function usePlaceOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: PlaceOrderVariables) =>
      createOrder(input, idempotencyKey),
    onSuccess: (order) => {
      queryClient.setQueryData(ORDER_KEYS.detail(order.id), order)
      // The checkout spent the cart; the badge and the cart page must follow.
      void queryClient.invalidateQueries({ queryKey: CART_KEYS.cart })
      void queryClient.invalidateQueries({ queryKey: ORDER_KEYS.all })
    },
  })
}

export function useStartPayment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (orderId: string) => startMockPayment(orderId),
    onSuccess: (payment) => {
      void queryClient.invalidateQueries({
        queryKey: ORDER_KEYS.detail(payment.orderId),
      })
      void queryClient.invalidateQueries({
        queryKey: ORDER_KEYS.payments(payment.orderId),
      })
    },
  })
}

export function useConfirmPayment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      paymentId,
      approve,
    }: {
      paymentId: string
      approve: boolean
    }) => confirmMockPayment(paymentId, approve),
    onSuccess: (result) => {
      queryClient.setQueryData<OrderDetail | undefined>(
        ORDER_KEYS.detail(result.orderId),
        (current) =>
          current
            ? {
                ...current,
                status: result.orderStatus,
                paymentStatus: result.paymentStatus,
              }
            : current,
      )
      void queryClient.invalidateQueries({
        queryKey: ORDER_KEYS.detail(result.orderId),
      })
      void queryClient.invalidateQueries({ queryKey: ORDER_KEYS.all })
    },
  })
}

export function useCancelOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (orderId: string) => cancelOrder(orderId),
    onSuccess: (order) => {
      queryClient.setQueryData(ORDER_KEYS.detail(order.id), order)
      void queryClient.invalidateQueries({ queryKey: ORDER_KEYS.all })
    },
  })
}

export function useRequestReturn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (orderId: string) => requestOrderReturn(orderId),
    onSuccess: (order) => {
      queryClient.setQueryData(ORDER_KEYS.detail(order.id), order)
      void queryClient.invalidateQueries({ queryKey: ORDER_KEYS.all })
    },
  })
}

export function usePayments(orderId: string | undefined) {
  return useQuery({
    queryKey: ORDER_KEYS.payments(orderId ?? ""),
    queryFn: () => listPayments(orderId as string),
    enabled: Boolean(orderId),
  })
}

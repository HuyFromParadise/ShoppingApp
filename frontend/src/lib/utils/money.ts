export const DEFAULT_CURRENCY = "VND"

/**
 * Formats an integer amount in minor currency units.
 *
 * VND has no subunit (ADR-008), so the stored amount is the đồng value itself and
 * is never divided. This is the single money formatter used by the storefront.
 */
export function formatMoney(
  amountMinor: number,
  currency: string = DEFAULT_CURRENCY,
): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amountMinor)
}

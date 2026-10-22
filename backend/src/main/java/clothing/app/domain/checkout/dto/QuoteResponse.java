package clothing.app.domain.checkout.dto;

/**
 * Server-computed totals the checkout UI renders. Amounts are VND minor units,
 * like the cart; the client never adds them up itself.
 */
public record QuoteResponse(
		String currency,
		int itemCount,
		long subtotalMinor,
		long discountTotalMinor,
		long shippingTotalMinor,
		long grandTotalMinor,
		String shippingMethodCode,
		String shippingMethodName) {
}

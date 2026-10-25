package clothing.app.domain.order.dto;

import java.time.Instant;

/** One row of the customer's order history (DESIGN_REFERENCE §5.3, Journey D). */
public record OrderSummaryResponse(
		String id,
		String orderNumber,
		String status,
		String paymentStatus,
		String currency,
		long grandTotalMinor,
		int itemCount,
		Instant placedAt) {
}

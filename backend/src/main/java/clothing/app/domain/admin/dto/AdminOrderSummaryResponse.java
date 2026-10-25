package clothing.app.domain.admin.dto;

import java.time.Instant;

/** One row of the admin order list (DESIGN_REFERENCE §5.3, Phase 6). */
public record AdminOrderSummaryResponse(
		String id,
		String orderNumber,
		String status,
		String paymentStatus,
		String currency,
		long grandTotalMinor,
		int itemCount,
		String customerId,
		String customerEmail,
		String shipRecipientName,
		Instant placedAt) {
}

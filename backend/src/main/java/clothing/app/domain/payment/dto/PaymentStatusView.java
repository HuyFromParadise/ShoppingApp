package clothing.app.domain.payment.dto;

import java.time.Instant;

/** One payment attempt, as the customer's order page shows it. */
public record PaymentStatusView(
		String id,
		String provider,
		String status,
		Long amountMinor,
		String currency,
		String providerTransactionId,
		Instant createdAt) {
}

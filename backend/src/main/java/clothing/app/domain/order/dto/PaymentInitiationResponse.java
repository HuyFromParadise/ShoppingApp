package clothing.app.domain.order.dto;

import java.time.Instant;

/** The start of a payment: where to send the customer and what was stored. */
public record PaymentInitiationResponse(
		String paymentId,
		String orderId,
		String provider,
		String status,
		String providerTransactionId,
		String paymentUrl,
		boolean mock,
		Instant createdAt) {
}

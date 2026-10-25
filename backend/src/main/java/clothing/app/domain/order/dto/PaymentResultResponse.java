package clothing.app.domain.order.dto;

import java.time.Instant;

/** Result of settling a payment, shown to the customer after a mock/real redirect. */
public record PaymentResultResponse(
		String orderId,
		String orderNumber,
		String orderStatus,
		String paymentStatus,
		String provider,
		boolean paid,
		Instant settledAt) {
}

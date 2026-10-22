package clothing.app.domain.order.dto;

import java.time.Instant;
import java.util.List;

/** The order the customer just created (DESIGN_REFERENCE §5.3, §8). */
public record OrderResponse(
		String id,
		String orderNumber,
		String status,
		String paymentStatus,
		String currency,
		long subtotalMinor,
		long discountTotalMinor,
		long shippingTotalMinor,
		long grandTotalMinor,
		String shippingMethodCode,
		String shippingMethodName,
		OrderAddressResponse shippingAddress,
		String customerNote,
		Instant placedAt,
		List<OrderItemResponse> items) {
}

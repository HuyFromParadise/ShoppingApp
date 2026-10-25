package clothing.app.domain.order.dto;

import java.time.Instant;
import java.util.List;

/**
 * An order the customer owns (DESIGN_REFERENCE §5.3): the same shape as the
 * creation response plus the status history, because the detail page shows it.
 */
public record OrderDetailResponse(
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
		List<OrderItemResponse> items,
		List<OrderStatusHistoryResponse> statusHistory) {
}

package clothing.app.domain.order.dto;

import java.time.Instant;
import java.util.List;

/** One fulfillment status change, in order (PROJECT_SPEC Journey D). */
public record OrderStatusHistoryResponse(
		String fromStatus,
		String toStatus,
		String note,
		Instant changedAt) {
}

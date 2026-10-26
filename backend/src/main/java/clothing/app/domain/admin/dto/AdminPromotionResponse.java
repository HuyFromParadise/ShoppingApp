package clothing.app.domain.admin.dto;

import java.time.Instant;

/** A promotion as the admin list shows it. */
public record AdminPromotionResponse(
		String id,
		String code,
		String name,
		String description,
		String type,
		long value,
		long minOrderMinor,
		Long maxDiscountMinor,
		Instant startsAt,
		Instant endsAt,
		boolean active) {
}

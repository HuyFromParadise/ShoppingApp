package clothing.app.domain.admin.dto;

import java.time.Instant;

/** A product as the admin list shows it, including non-public statuses. */
public record AdminProductSummaryResponse(
		String id,
		String name,
		String slug,
		String categoryId,
		String categoryName,
		long basePriceMinor,
		String currency,
		String status,
		boolean featured,
		int variantCount,
		int totalStock,
		String primaryImageUrl,
		Instant createdAt,
		Instant updatedAt) {
}

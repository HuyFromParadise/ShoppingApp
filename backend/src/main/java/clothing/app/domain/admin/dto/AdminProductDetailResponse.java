package clothing.app.domain.admin.dto;

import java.time.Instant;
import java.util.List;

/** Full admin product, including every variant whether active or not. */
public record AdminProductDetailResponse(
		String id,
		String name,
		String slug,
		String shortDescription,
		String description,
		String categoryId,
		String categoryName,
		long basePriceMinor,
		String currency,
		String status,
		String material,
		String fit,
		String careInstructions,
		boolean featured,
		List<AdminImageResponse> images,
		List<AdminVariantResponse> variants,
		Instant createdAt,
		Instant updatedAt) {
}

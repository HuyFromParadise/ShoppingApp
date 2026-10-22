package clothing.app.catalog.product.dto;

import java.time.Instant;

import clothing.app.catalog.category.dto.CategorySummaryResponse;

public record ProductSummaryResponse(
	String id,
	String name,
	String slug,
	String shortDescription,
	CategorySummaryResponse category,
	long priceMinor,
	Long compareAtPriceMinor,
	String currency,
	boolean onSale,
	boolean inStock,
	boolean featured,
	ProductImageResponse primaryImage,
	Instant createdAt
) {
}

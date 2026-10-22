package clothing.app.catalog.product.dto;

import java.time.Instant;
import java.util.List;

import clothing.app.catalog.category.dto.CategorySummaryResponse;

public record ProductDetailResponse(
	String id,
	String name,
	String slug,
	String shortDescription,
	String description,
	CategorySummaryResponse category,
	long basePriceMinor,
	long priceMinor,
	Long compareAtPriceMinor,
	String currency,
	boolean onSale,
	boolean inStock,
	boolean featured,
	String material,
	String fit,
	String careInstructions,
	List<ProductImageResponse> images,
	List<ProductVariantResponse> variants,
	Instant createdAt,
	Instant updatedAt
) {
}

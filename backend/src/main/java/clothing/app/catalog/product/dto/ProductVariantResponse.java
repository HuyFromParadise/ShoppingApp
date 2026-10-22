package clothing.app.catalog.product.dto;

public record ProductVariantResponse(
	String id,
	String sku,
	String size,
	String color,
	long priceMinor,
	Long compareAtPriceMinor,
	int stockQuantity,
	boolean inStock
) {
}

package clothing.app.domain.admin.dto;

/** One variant, with the price the storefront would charge. */
public record AdminVariantResponse(
		String id,
		String sku,
		String size,
		String color,
		Long priceOverrideMinor,
		long effectivePriceMinor,
		int stockQuantity,
		boolean active) {
}

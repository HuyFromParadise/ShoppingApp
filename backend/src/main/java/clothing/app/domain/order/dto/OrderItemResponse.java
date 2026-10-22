package clothing.app.domain.order.dto;

/**
 * A purchased line, with the product data it had at purchase time so order
 * history survives catalog edits.
 */
public record OrderItemResponse(
		String id,
		String variantId,
		String productName,
		String sku,
		String size,
		String color,
		String imageUrl,
		long unitPriceMinor,
		int quantity,
		long lineTotalMinor) {
}

package clothing.app.domain.cart.dto;

/**
 * One line of the cart. Prices are minor units and are recomputed from the
 * catalog on every read, so a price change is visible before checkout.
 *
 * @param available the line can be bought right now: the product is ACTIVE,
 *                  the variant is active and the stock covers the quantity
 */
public record CartItemResponse(
		String id,
		String variantId,
		String productId,
		String productName,
		String productSlug,
		String imageUrl,
		String sku,
		String size,
		String color,
		long unitPriceMinor,
		int quantity,
		long lineTotalMinor,
		int stockQuantity,
		boolean available) {
}

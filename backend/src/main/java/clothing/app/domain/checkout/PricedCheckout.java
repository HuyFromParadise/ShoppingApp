package clothing.app.domain.checkout;

import java.util.List;

import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

/**
 * Server-computed checkout: the lines, the address and the money. The quote
 * endpoint renders it; order creation stores exactly these numbers, so the
 * customer never sees a total different from the one written to the order.
 */
public record PricedCheckout(
		String cartId,
		AddressEntity address,
		List<Line> lines,
		int itemCount,
		long subtotalMinor,
		long discountTotalMinor,
		long shippingTotalMinor,
		long grandTotalMinor,
		String currency,
		ShippingStrategy.Method shippingMethod) {

	/** One cart line with the catalog price it is charged at. */
	public record Line(
			ProductEntity product,
			ProductVariantEntity variant,
			String imageUrl,
			int quantity,
			long unitPrice,
			long lineTotal) {
	}
}

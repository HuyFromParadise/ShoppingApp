package clothing.app.domain.cart.dto;

import java.util.List;

/** The whole cart, so one request is enough to re-render it after a change. */
public record CartResponse(
		String id,
		List<CartItemResponse> items,
		int itemCount,
		long subtotalMinor,
		String currency) {
}

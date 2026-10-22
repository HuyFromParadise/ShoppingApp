package clothing.app.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Order creation request (DESIGN_REFERENCE §5.3, §8). Amounts are never part of
 * the request: the server prices the cart itself.
 */
public record CreateOrderRequest(
		@NotBlank @Size(max = 36) String addressId,
		@NotBlank @Size(max = 40) String shippingMethod,
		@Size(max = 40) String promotionCode,
		@Size(max = 500) String customerNote) {
}

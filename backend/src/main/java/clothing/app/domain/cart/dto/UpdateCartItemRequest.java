package clothing.app.domain.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Replacement quantity of a cart line. */
public record UpdateCartItemRequest(
		@NotNull @Min(1) @Max(AddCartItemRequest.MAX_LINE_QUANTITY) Integer quantity) {
}

package clothing.app.domain.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A line to add to the cart (DESIGN_REFERENCE §5.3). Adding a variant that is
 * already in the cart adds to its quantity, so the cap applies to the result.
 */
public record AddCartItemRequest(
		@NotBlank String variantId,
		@NotNull @Min(1) @Max(MAX_LINE_QUANTITY) Integer quantity) {

	/** Per-line cap from the validation table (DESIGN_REFERENCE §5.4). */
	public static final int MAX_LINE_QUANTITY = 10;
}

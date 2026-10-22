package clothing.app.domain.checkout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Checkout quote request (DESIGN_REFERENCE §5.3). */
public record QuoteRequest(
		@NotBlank @Size(max = 36) String addressId,
		@NotBlank @Size(max = 40) String shippingMethod,
		@Size(max = 40) String promotionCode) {
}

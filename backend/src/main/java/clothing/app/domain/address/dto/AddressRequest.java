package clothing.app.domain.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New shipping address. Lengths mirror the `addresses` columns
 * (DESIGN_REFERENCE §4).
 */
public record AddressRequest(
		@NotBlank @Size(max = 100) String recipientName,
		@Size(max = 30) String phone,
		@NotBlank @Size(max = 255) String addressLine1,
		@Size(max = 255) String addressLine2,
		@Size(max = 100) String ward,
		@Size(max = 100) String district,
		@NotBlank @Size(max = 100) String cityOrProvince,
		@Size(max = 20) String postalCode,
		Boolean isDefault) {
}

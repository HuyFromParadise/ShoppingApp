package clothing.app.domain.address.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a shipping address: an absent field (null) is left
 * unchanged, an empty string clears an optional field. A required field may
 * not be blanked, so it is rejected when it holds only whitespace.
 */
public record AddressUpdateRequest(
		@Pattern(regexp = ".*\\S.*", message = "Recipient name must not be blank")
		@Size(max = 100) String recipientName,
		@Size(max = 30) String phone,
		@Pattern(regexp = ".*\\S.*", message = "Address line 1 must not be blank")
		@Size(max = 255) String addressLine1,
		@Size(max = 255) String addressLine2,
		@Size(max = 100) String ward,
		@Size(max = 100) String district,
		@Pattern(regexp = ".*\\S.*", message = "City or province must not be blank")
		@Size(max = 100) String cityOrProvince,
		@Size(max = 20) String postalCode,
		Boolean isDefault) {
}

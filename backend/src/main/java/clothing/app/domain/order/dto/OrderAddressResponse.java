package clothing.app.domain.order.dto;

/**
 * Immutable copy of the delivery address taken when the order was placed. The
 * address book may change later; the order keeps what the customer bought with.
 */
public record OrderAddressResponse(
		String recipientName,
		String phone,
		String addressLine1,
		String addressLine2,
		String ward,
		String district,
		String cityOrProvince,
		String postalCode) {
}

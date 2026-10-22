package clothing.app.domain.address.dto;

import clothing.app.domain.address.AddressEntity;

/** A shipping address as the client sees it. */
public record AddressResponse(String id, String recipientName, String phone, String addressLine1,
		String addressLine2, String ward, String district, String cityOrProvince, String postalCode,
		boolean isDefault) {

	public static AddressResponse from(AddressEntity address) {
		return new AddressResponse(address.getId(), address.getRecipientName(), address.getPhone(),
			address.getAddressLine1(), address.getAddressLine2(), address.getWard(),
			address.getDistrict(), address.getCityOrProvince(), address.getPostalCode(),
			Boolean.TRUE.equals(address.getIsDefault()));
	}
}

package clothing.app.domain.address;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.address.dto.AddressRequest;
import clothing.app.domain.address.dto.AddressResponse;
import clothing.app.domain.address.dto.AddressUpdateRequest;

/**
 * Shipping addresses of the signed-in customer.
 *
 * <p>Ownership is enforced here, never inferred from the URL: an address that
 * belongs to somebody else is reported as missing so its existence is not
 * confirmed. At most one address per user is the default, which the database
 * enforces through the generated `default_owner` column; this service clears
 * the previous default before promoting a new one.
 */
@Service
public class AddressService {

	private final AddressRepository addressRepository;

	public AddressService(AddressRepository addressRepository) {
		this.addressRepository = addressRepository;
	}

	@Transactional(readOnly = true)
	public List<AddressResponse> list(String userId) {
		return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
			.map(AddressResponse::from)
			.toList();
	}

	@Transactional
	public AddressResponse create(String userId, AddressRequest request) {
		requireUser(userId);
		AddressEntity address = new AddressEntity();
		address.setUserId(userId);
		address.setRecipientName(request.recipientName().trim());
		address.setPhone(trimToNull(request.phone()));
		address.setAddressLine1(request.addressLine1().trim());
		address.setAddressLine2(trimToNull(request.addressLine2()));
		address.setWard(trimToNull(request.ward()));
		address.setDistrict(trimToNull(request.district()));
		address.setCityOrProvince(request.cityOrProvince().trim());
		address.setPostalCode(trimToNull(request.postalCode()));
		address.setIsDefault(Boolean.TRUE.equals(request.isDefault()));

		if (address.getIsDefault()) {
			clearDefault(userId, null);
		}
		return AddressResponse.from(save(address));
	}

	@Transactional
	public AddressResponse update(String userId, String addressId, AddressUpdateRequest request) {
		requireUser(userId);
		AddressEntity address = findOwned(userId, addressId);

		if (Boolean.TRUE.equals(request.isDefault())) {
			clearDefault(userId, addressId);
			address.setIsDefault(Boolean.TRUE);
		}
		else if (Boolean.FALSE.equals(request.isDefault())) {
			address.setIsDefault(Boolean.FALSE);
		}
		if (request.recipientName() != null) address.setRecipientName(request.recipientName().trim());
		if (request.phone() != null) address.setPhone(trimToNull(request.phone()));
		if (request.addressLine1() != null) address.setAddressLine1(request.addressLine1().trim());
		if (request.addressLine2() != null) address.setAddressLine2(trimToNull(request.addressLine2()));
		if (request.ward() != null) address.setWard(trimToNull(request.ward()));
		if (request.district() != null) address.setDistrict(trimToNull(request.district()));
		if (request.cityOrProvince() != null) address.setCityOrProvince(request.cityOrProvince().trim());
		if (request.postalCode() != null) address.setPostalCode(trimToNull(request.postalCode()));

		return AddressResponse.from(save(address));
	}

	@Transactional
	public void delete(String userId, String addressId) {
		requireUser(userId);
		addressRepository.delete(findOwned(userId, addressId));
	}

	/**
	 * Saves the address. Two concurrent requests can both promote a default and
	 * the unique index rejects the second one; that is a conflict the customer
	 * can retry, not a server error.
	 */
	private AddressEntity save(AddressEntity address) {
		try {
			return addressRepository.saveAndFlush(address);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException.Conflict(
				"Another default address was set at the same time, please try again");
		}
	}

	/** The owner is always the authenticated customer. */
	private void requireUser(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
	}

	/** The address of this user, or NOT_FOUND - never another user's address. */
	private AddressEntity findOwned(String userId, String addressId) {
		return addressRepository.findByIdAndUserId(addressId, userId)
			.orElseThrow(() -> new ApiException.NotFound("Address not found"));
	}

	/**
	 * Unsets the current default so the unique index on the generated
	 * {@code default_owner} column is satisfied. The flush makes sure the
	 * updates hit the database before a new default is written.
	 */
	private void clearDefault(String userId, String exceptAddressId) {
		for (AddressEntity address : addressRepository.findByUserIdAndIsDefaultTrue(userId)) {
			if (!address.getId().equals(exceptAddressId)) {
				address.setIsDefault(Boolean.FALSE);
				addressRepository.save(address);
			}
		}
		addressRepository.flush();
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}

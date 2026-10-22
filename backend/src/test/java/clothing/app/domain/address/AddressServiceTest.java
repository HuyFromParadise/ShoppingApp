package clothing.app.domain.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.address.dto.AddressRequest;

/**
 * Rules that are hard to reach through HTTP: the unique index on the default
 * address and the guard against an unauthenticated caller.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

	@Mock
	private AddressRepository addressRepository;

	@Test
	void reportsAConcurrentDefaultAsConflict() {
		when(addressRepository.saveAndFlush(any(AddressEntity.class)))
			.thenThrow(new DataIntegrityViolationException("uk_addresses_default_owner"));

		AddressService service = new AddressService(addressRepository);

		assertThatThrownBy(() -> service.create("u1", request()))
			.isInstanceOf(ApiException.Conflict.class)
			.hasMessageContaining("Another default address");
	}

	@Test
	void refusesAnUnauthenticatedCaller() {
		AddressService service = new AddressService(addressRepository);

		assertThatThrownBy(() -> service.create(null, request()))
			.isInstanceOf(ApiException.Unauthenticated.class);
	}

	@Test
	void listsOnlyTheOwnAddresses() {
		when(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc("u1"))
			.thenReturn(List.of());

		AddressService service = new AddressService(addressRepository);

		assertThat(service.list("u1")).isEmpty();
	}

	@Test
	void reportsAForeignAddressAsMissing() {
		when(addressRepository.findByIdAndUserId("a1", "u1")).thenReturn(Optional.empty());

		AddressService service = new AddressService(addressRepository);

		assertThatThrownBy(() -> service.delete("u1", "a1"))
			.isInstanceOf(ApiException.NotFound.class);
	}

	private static AddressRequest request() {
		return new AddressRequest("An Nguyen", "0901234567", "12 Le Loi", null,
			"Ben Thanh", "District 1", "Ho Chi Minh City", "700000", Boolean.TRUE);
	}
}

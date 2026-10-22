package clothing.app.domain.address;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.address.dto.AddressRequest;
import clothing.app.domain.address.dto.AddressResponse;
import clothing.app.domain.address.dto.AddressUpdateRequest;

/**
 * Shipping addresses of the signed-in customer (DESIGN_REFERENCE §5.3).
 * The controller stays DTO-only; ownership is checked in the service.
 */
@RestController
@RequestMapping("/api/v1/account/addresses")
public class AddressController {

	private final AddressService addressService;

	public AddressController(AddressService addressService) {
		this.addressService = addressService;
	}

	@GetMapping
	public ApiResponse<List<AddressResponse>> list(@CurrentUser String userId) {
		return ApiResponse.success(addressService.list(userId));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AddressResponse>> create(@CurrentUser String userId,
			@Valid @RequestBody AddressRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(addressService.create(userId, request)));
	}

	@PatchMapping("/{id}")
	public ApiResponse<AddressResponse> update(@CurrentUser String userId, @PathVariable String id,
			@Valid @RequestBody AddressUpdateRequest request) {
		return ApiResponse.success(addressService.update(userId, id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@CurrentUser String userId, @PathVariable String id) {
		addressService.delete(userId, id);
		return ResponseEntity.noContent().build();
	}
}

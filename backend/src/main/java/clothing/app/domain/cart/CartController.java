package clothing.app.domain.cart;

import jakarta.validation.Valid;

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
import clothing.app.domain.cart.dto.AddCartItemRequest;
import clothing.app.domain.cart.dto.CartResponse;
import clothing.app.domain.cart.dto.UpdateCartItemRequest;

/**
 * Cart of the signed-in customer (DESIGN_REFERENCE §5.3). The customer always
 * comes from the access token; the cart is created on first use.
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	public ApiResponse<CartResponse> get(@CurrentUser String userId) {
		return ApiResponse.success(cartService.get(userId));
	}

	@PostMapping("/items")
	public ApiResponse<CartResponse> addItem(@CurrentUser String userId,
			@Valid @RequestBody AddCartItemRequest request) {
		return ApiResponse.success(cartService.addItem(userId, request));
	}

	@PatchMapping("/items/{itemId}")
	public ApiResponse<CartResponse> updateItem(@CurrentUser String userId,
			@PathVariable String itemId, @Valid @RequestBody UpdateCartItemRequest request) {
		return ApiResponse.success(cartService.updateItem(userId, itemId, request));
	}

	@DeleteMapping("/items/{itemId}")
	public ResponseEntity<Void> removeItem(@CurrentUser String userId, @PathVariable String itemId) {
		cartService.removeItem(userId, itemId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> clear(@CurrentUser String userId) {
		cartService.clear(userId);
		return ResponseEntity.noContent().build();
	}
}

package clothing.app.domain.wishlist;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;

/**
 * Wishlist (PROJECT_SPEC §7, P1): the customer's own saved products.
 *
 * <p>The customer always comes from the token, so one customer can never read or
 * change another's wishlist. The endpoints follow the spec's product-keyed
 * shape: {@code POST /wishlist/items/{productId}} and
 * {@code DELETE /wishlist/items/{productId}}.
 */
@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

	private final WishlistService wishlistService;

	public WishlistController(WishlistService wishlistService) {
		this.wishlistService = wishlistService;
	}

	@GetMapping
	public ApiResponse<List<WishlistService.WishlistEntry>> list(@CurrentUser String userId) {
		return ApiResponse.success(wishlistService.list(userId));
	}

	@PostMapping("/items/{productId}")
	public ApiResponse<List<WishlistService.WishlistEntry>> save(@CurrentUser String userId,
			@PathVariable String productId) {
		return ApiResponse.success(wishlistService.save(userId, productId));
	}

	@DeleteMapping("/items/{productId}")
	public ApiResponse<List<WishlistService.WishlistEntry>> remove(@CurrentUser String userId,
			@PathVariable String productId) {
		return ApiResponse.success(wishlistService.remove(userId, productId));
	}

	/** Toggle state for a product page; false for a visitor, never an error. */
	@GetMapping("/items/{productId}")
	public ApiResponse<Boolean> contains(@CurrentUser String userId,
			@PathVariable String productId) {
		return ApiResponse.success(wishlistService.contains(userId, productId));
	}
}

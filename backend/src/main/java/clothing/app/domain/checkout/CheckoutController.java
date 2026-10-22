package clothing.app.domain.checkout;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.checkout.dto.QuoteRequest;
import clothing.app.domain.checkout.dto.QuoteResponse;

/**
 * Checkout quote (DESIGN_REFERENCE §5.3). It is a read: no stock is held and
 * nothing is written, so the UI can re-quote when the address or shipping
 * option changes.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

	private final CheckoutService checkoutService;

	public CheckoutController(CheckoutService checkoutService) {
		this.checkoutService = checkoutService;
	}

	@PostMapping("/quote")
	public ApiResponse<QuoteResponse> quote(@CurrentUser String userId,
			@Valid @RequestBody QuoteRequest request) {
		return ApiResponse.success(checkoutService.quote(userId, request));
	}
}

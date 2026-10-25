package clothing.app.domain.payment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.domain.order.dto.PaymentResultResponse;

/**
 * VNPay webhook/return callback (DESIGN_REFERENCE §5.3). Public at the security
 * filter level on purpose: it is authenticated by the HMAC signature inside
 * {@link VnPayGateway#verify}, not by a user session, and protected by the
 * per-IP rate limiter. Every event is deduplicated by {@code payment_events}.
 *
 * <p>Accepted as either form fields (server-to-server) or query parameters
 * (browser return).
 */
@RestController
@RequestMapping("/api/v1/payments/webhooks/vnpay")
public class VnPayWebhookController {

	private final PaymentService paymentService;

	public VnPayWebhookController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public ApiResponse<PaymentResultResponse> callback(
			@RequestParam Map<String, String> params) {
		return ApiResponse.success(paymentService.handleVnPayCallback(flatten(params)));
	}

	@GetMapping
	public ApiResponse<PaymentResultResponse> returnPage(HttpServletRequest request) {
		return ApiResponse.success(paymentService.handleVnPayCallback(flatten(query(request))));
	}

	private static Map<String, String> query(HttpServletRequest request) {
		Map<String, String> params = new LinkedHashMap<>();
		request.getParameterMap().forEach((key, values) -> {
			if (values.length > 0) {
				params.put(key, values[0]);
			}
		});
		return params;
	}

	private static Map<String, String> flatten(Map<String, String> params) {
		return params == null ? Map.of() : new LinkedHashMap<>(params);
	}
}

package clothing.app.domain.payment;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.order.dto.PaymentInitiationResponse;
import clothing.app.domain.order.dto.PaymentResultResponse;
import clothing.app.domain.payment.dto.PaymentStatusView;

/**
 * Payment entry points for the signed-in customer (DESIGN_REFERENCE §5.3).
 *
 * <p>VNPay returns {@code PROVIDER_NOT_CONFIGURED} until real credentials exist
 * (ADR-007). The mock endpoints are the labelled development flow: they never
 * touch a real provider and the response says {@code mock: true}.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

	private final PaymentService paymentService;
	private final String returnUrl;

	public PaymentController(PaymentService paymentService,
			@Value("${app.payment.vnpay.return-url:}") String returnUrl) {
		this.paymentService = paymentService;
		this.returnUrl = returnUrl;
	}

	@PostMapping("/{orderId}/vnpay/create")
	public ApiResponse<PaymentInitiationResponse> createVnPay(@CurrentUser String userId,
			@PathVariable String orderId, HttpServletRequest request) {
		return ApiResponse.success(paymentService.createVnPay(userId, orderId,
			clientIp(request), returnUrl));
	}

	@PostMapping("/{orderId}/mock/create")
	public ApiResponse<PaymentInitiationResponse> createMock(@CurrentUser String userId,
			@PathVariable String orderId, HttpServletRequest request) {
		return ApiResponse.success(
			paymentService.createMock(userId, orderId, clientIp(request)));
	}

	/** Settles a mock payment started by {@code createMock}. */
	@PostMapping("/mock/{paymentId}/confirm")
	public ApiResponse<PaymentResultResponse> confirmMock(@CurrentUser String userId,
			@PathVariable String paymentId,
			@RequestParam(defaultValue = "true") boolean approve) {
		return ApiResponse.success(paymentService.confirmMock(userId, paymentId, approve));
	}

	@GetMapping("/{orderId}")
	public ApiResponse<List<PaymentStatusView>> list(@CurrentUser String userId,
			@PathVariable String orderId) {
		return ApiResponse.success(paymentService.listOwn(userId, orderId));
	}

	static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}

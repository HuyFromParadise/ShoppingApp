package clothing.app.domain.admin;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.exception.ApiException;
import clothing.app.common.response.ApiResponse;
import clothing.app.common.response.PageResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.admin.dto.AdminOrderSummaryResponse;
import clothing.app.domain.order.OrderService;
import clothing.app.domain.order.dto.OrderDetailResponse;
import clothing.app.domain.order.dto.UpdateOrderStatusRequest;

/**
 * Admin order access (DESIGN_REFERENCE §5.3, Phase 6): a filtered list, one
 * order, and the status change.
 *
 * <p>{@link OrderService#changeStatusByAdmin} is the only writer: it applies the
 * order state machine and its restock/refund side effects through the same code
 * path the customer and payment flows use. Access is limited to ADMIN by the
 * security filter chain.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

	private final OrderService orderService;
	private final AdminOrderService adminOrderService;

	public AdminOrderController(OrderService orderService, AdminOrderService adminOrderService) {
		this.orderService = orderService;
		this.adminOrderService = adminOrderService;
	}

	/**
	 * @param q order-number text, or part of a customer email when it contains
	 *     {@code @}
	 */
	@GetMapping
	public PageResponse<AdminOrderSummaryResponse> list(@CurrentUser String adminId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String placedFrom,
			@RequestParam(required = false) String placedBefore,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminOrderService.list(status, q, parseInstant("placedFrom", placedFrom),
			parseInstant("placedBefore", placedBefore), page, size);
	}

	/** An ISO-8601 timestamp; a bad value is a field error, not a 500. */
	private static Instant parseInstant(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(value.trim());
		}
		catch (DateTimeParseException ex) {
			throw new ApiException.Validation(
				field + " must be an ISO-8601 timestamp",
				Map.of(field, "Use an ISO-8601 timestamp, e.g. 2026-10-25T00:00:00Z"));
		}
	}

	@GetMapping("/{orderId}")
	public ApiResponse<OrderDetailResponse> detail(@CurrentUser String adminId,
			@PathVariable String orderId) {
		return ApiResponse.success(orderService.getAny(orderId));
	}

	@PatchMapping("/{orderId}/status")
	public ApiResponse<OrderDetailResponse> changeStatus(@CurrentUser String adminId,
			@PathVariable String orderId,
			@Valid @RequestBody UpdateOrderStatusRequest request) {
		return ApiResponse.success(orderService.changeStatusByAdmin(orderId, request));
	}
}

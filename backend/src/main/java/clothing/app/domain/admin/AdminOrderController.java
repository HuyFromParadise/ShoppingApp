package clothing.app.domain.admin;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.response.PageResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.order.OrderService;
import clothing.app.domain.order.dto.OrderDetailResponse;
import clothing.app.domain.order.dto.OrderSummaryResponse;
import clothing.app.domain.order.dto.UpdateOrderStatusRequest;

/**
 * Admin order access (DESIGN_REFERENCE §5.3, Phase 6). This step only supplies
 * the status-change contract: {@link OrderService#changeStatusByAdmin} applies
 * the {@code OrderStateMachine} and its restock/refund side effects. Listing
 * filters and the dashboard belong to the admin step. Access is limited to
 * ADMIN by the security filter chain.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

	private final OrderService orderService;

	public AdminOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public PageResponse<OrderSummaryResponse> list(@CurrentUser String adminId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return orderService.listAll(page, size);
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

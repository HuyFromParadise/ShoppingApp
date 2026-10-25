package clothing.app.domain.order;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import clothing.app.common.response.ApiResponse;
import clothing.app.common.response.PageResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.order.dto.CreateOrderRequest;
import clothing.app.domain.order.dto.OrderDetailResponse;
import clothing.app.domain.order.dto.OrderResponse;
import clothing.app.domain.order.dto.OrderSummaryResponse;

/**
 * Orders of the signed-in customer (DESIGN_REFERENCE §5.3). The customer always
 * comes from the access token; ownership of a specific order is checked in
 * {@link OrderService}. {@code Idempotency-Key} is validated in the service so a
 * missing header yields the standard error envelope instead of a 500.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final OrderCreationService orderCreationService;
	private final OrderService orderService;

	public OrderController(OrderCreationService orderCreationService,
			OrderService orderService) {
		this.orderCreationService = orderCreationService;
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<OrderResponse>> create(@CurrentUser String userId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(orderCreationService.create(userId, idempotencyKey, request)));
	}

	@GetMapping
	public PageResponse<OrderSummaryResponse> list(@CurrentUser String userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return orderService.listOwn(userId, page, size);
	}

	@GetMapping("/{orderId}")
	public ApiResponse<OrderDetailResponse> detail(@CurrentUser String userId,
			@PathVariable String orderId) {
		return ApiResponse.success(orderService.getOwn(userId, orderId));
	}

	@PostMapping("/{orderId}/cancel")
	public ApiResponse<OrderDetailResponse> cancel(@CurrentUser String userId,
			@PathVariable String orderId) {
		return ApiResponse.success(orderService.cancelOwn(userId, orderId));
	}

	@PostMapping("/{orderId}/return")
	public ApiResponse<OrderDetailResponse> requestReturn(@CurrentUser String userId,
			@PathVariable String orderId) {
		return ApiResponse.success(orderService.requestReturn(userId, orderId));
	}
}

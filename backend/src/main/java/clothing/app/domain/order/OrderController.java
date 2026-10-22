package clothing.app.domain.order;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.order.dto.CreateOrderRequest;
import clothing.app.domain.order.dto.OrderResponse;

/**
 * Order creation (DESIGN_REFERENCE §5.3). {@code Idempotency-Key} is required;
 * it is validated in the service so a missing header yields the standard error
 * envelope instead of a 500.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<OrderResponse>> create(@CurrentUser String userId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(orderService.create(userId, idempotencyKey, request)));
	}
}

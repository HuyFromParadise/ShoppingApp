package clothing.app.domain.order.dto;

/** Admin decision on an order (Phase 6 endpoint). */
public record UpdateOrderStatusRequest(
		@jakarta.validation.constraints.NotBlank(message = "Status is required")
		String status,
		@jakarta.validation.constraints.Size(max = 500, message = "Must be at most 500 characters")
		String note) {
}

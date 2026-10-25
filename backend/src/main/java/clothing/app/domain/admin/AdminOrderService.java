package clothing.app.domain.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import clothing.app.common.exception.ApiException;
import clothing.app.common.response.PageResponse;
import clothing.app.domain.admin.dto.AdminOrderSummaryResponse;
import clothing.app.domain.order.AdminOrderSpecifications;
import clothing.app.domain.order.OrderEntity;
import clothing.app.domain.order.OrderItemEntity;
import clothing.app.domain.order.OrderItemRepository;
import clothing.app.domain.order.OrderRepository;
import clothing.app.domain.user.UserEntity;
import clothing.app.domain.user.UserRepository;

/**
 * Admin order listing (DESIGN_REFERENCE §5.3, Phase 6). Read-only: the status
 * change stays on {@code AdminOrderController} and the state machine, so there
 * is exactly one way an order's status moves.
 *
 * <p>Filters are optional and combine: status, order-number or customer-email
 * text, and a placed-at window. The window is expressed as inclusive
 * {@code from} and exclusive {@code to} so paging over a period never
 * double-counts an order at a boundary.
 */
@Service
public class AdminOrderService {

	static final int MAX_PAGE_SIZE = 48;

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final UserRepository userRepository;

	public AdminOrderService(OrderRepository orderRepository,
			OrderItemRepository orderItemRepository, UserRepository userRepository) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminOrderSummaryResponse> list(String status, String q, Instant placedFrom,
			Instant placedBefore, int page, int size) {
		validatePage(page, size);
		validateStatus(status);
		if (placedFrom != null && placedBefore != null && !placedFrom.isBefore(placedBefore)) {
			throw new ApiException.Validation("placedFrom must be before placedTo",
				Map.of("placedFrom", "Must be before placedBefore"));
		}

		PageRequest pageable = PageRequest.of(page, size,
			Sort.by(Sort.Direction.DESC, "placedAt"));

		Page<OrderEntity> orders;
		if (StringUtils.hasText(q) && looksLikeEmail(q)) {
			List<String> userIds = orderRepository
				.findUserIdsByEmailLike("%" + q.trim().toLowerCase(Locale.ROOT) + "%");
			orders = userIds.isEmpty()
				? Page.empty(pageable)
				: orderRepository.findAll(AdminOrderSpecifications.matchingCustomers(status, userIds,
					placedFrom, placedBefore), pageable);
		}
		else {
			orders = orderRepository.findAll(
				AdminOrderSpecifications.matching(status, q, placedFrom, placedBefore), pageable);
		}

		Map<String, String> emails = emailsOf(orders.getContent());
		List<AdminOrderSummaryResponse> rows = orders.getContent().stream()
			.map(order -> toSummary(order, emails.get(order.getUserId())))
			.toList();
		return PageResponse.of(rows, page, size, orders.getTotalElements());
	}

	/* --- helpers ------------------------------------------------------------ */

	private static boolean looksLikeEmail(String q) {
		return q.contains("@");
	}

	private void validateStatus(String status) {
		if (!StringUtils.hasText(status)) {
			return;
		}
		try {
			OrderEntity.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException.Validation("Unknown order status",
				Map.of("status", "Unknown order status"));
		}
	}

	private Map<String, String> emailsOf(List<OrderEntity> orders) {
		List<String> ids = orders.stream().map(OrderEntity::getUserId).distinct().toList();
		Map<String, String> emails = new HashMap<>();
		for (UserEntity user : userRepository.findAllById(ids)) {
			emails.put(user.getId(), user.getEmail());
		}
		return emails;
	}

	private AdminOrderSummaryResponse toSummary(OrderEntity order, String customerEmail) {
		List<OrderItemEntity> items = orderItemRepository
			.findByOrderIdOrderByCreatedAtAsc(order.getId());
		int itemCount = items.stream().mapToInt(OrderItemEntity::getQuantity).sum();
		return new AdminOrderSummaryResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getStatus().name(),
			order.getPaymentStatus().name(),
			order.getCurrency(),
			order.getGrandTotal(),
			itemCount,
			order.getUserId(),
			customerEmail,
			order.getShipRecipientName(),
			order.getPlacedAt());
	}

	static void validatePage(int page, int size) {
		if (page < 0) {
			throw new ApiException.Validation("Page must be zero or greater",
				Map.of("page", "Must be 0 or greater"));
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException.Validation("Page size must be between 1 and " + MAX_PAGE_SIZE,
				Map.of("size", "Must be between 1 and " + MAX_PAGE_SIZE));
		}
	}
}

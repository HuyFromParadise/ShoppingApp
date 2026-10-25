package clothing.app.domain.order;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;
import clothing.app.common.response.PageResponse;
import clothing.app.domain.order.OrderEntity.Status;
import clothing.app.domain.order.dto.OrderAddressResponse;
import clothing.app.domain.order.dto.OrderDetailResponse;
import clothing.app.domain.order.dto.OrderItemResponse;
import clothing.app.domain.order.dto.OrderStatusHistoryResponse;
import clothing.app.domain.order.dto.OrderSummaryResponse;
import clothing.app.domain.order.dto.UpdateOrderStatusRequest;

/**
 * Order reads and status changes by the customer and by an administrator
 * (DESIGN_REFERENCE §5.3, §7). Ownership is checked here, never inferred from
 * the URL, and every change goes through {@link OrderStatusService} so the
 * state machine is the only way a status moves.
 */
@Service
public class OrderService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 48;
	private static final int MAX_NOTE_LENGTH = 500;

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderStatusHistoryRepository historyRepository;
	private final OrderStatusService orderStatusService;

	public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
			OrderStatusHistoryRepository historyRepository,
			OrderStatusService orderStatusService) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.historyRepository = historyRepository;
		this.orderStatusService = orderStatusService;
	}

	/* --- reads -------------------------------------------------------------- */

	/** The signed-in customer's own orders, newest first. */
	@Transactional(readOnly = true)
	public PageResponse<OrderSummaryResponse> listOwn(String userId, int page, int size) {
		requireUser(userId);
		PageRequest pageable = PageRequest.of(validatePage(page), validateSize(size));
		return toPage(orderRepository.findByUserIdOrderByPlacedAtDesc(userId, pageable), pageable);
	}

	/** Every customer's orders, newest first (admin list; Phase 6 adds filters). */
	@Transactional(readOnly = true)
	public PageResponse<OrderSummaryResponse> listAll(int page, int size) {
		PageRequest pageable = PageRequest.of(validatePage(page), validateSize(size));
		return toPage(orderRepository.findAllByOrderByPlacedAtDesc(pageable), pageable);
	}

	/** One order with items and history; another customer's order is a 404, not a 403. */
	@Transactional(readOnly = true)
	public OrderDetailResponse getOwn(String userId, String orderId) {
		requireUser(userId);
		OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
			.orElseThrow(OrderService::notFound);
		return toDetail(order);
	}

	/** Admin view of any order. */
	@Transactional(readOnly = true)
	public OrderDetailResponse getAny(String orderId) {
		OrderEntity order = orderRepository.findById(orderId)
			.orElseThrow(OrderService::notFound);
		return toDetail(order);
	}

	/* --- customer actions --------------------------------------------------- */

	/** {@code PENDING_PAYMENT} or {@code CONFIRMED}; restocks and refunds if paid. */
	@Transactional
	public OrderDetailResponse cancelOwn(String userId, String orderId) {
		requireUser(userId);
		requireOwned(orderId, userId);
		orderStatusService.transition(orderId, Status.CANCELLED, OrderStateMachine.Actor.CUSTOMER,
			userId, "Cancelled by customer");
		return toDetail(reload(orderId));
	}

	/** {@code SHIPPED} or {@code DELIVERED}; the admin then accepts or rejects it. */
	@Transactional
	public OrderDetailResponse requestReturn(String userId, String orderId) {
		requireUser(userId);
		requireOwned(orderId, userId);
		orderStatusService.transition(orderId, Status.RETURN_REQUESTED,
			OrderStateMachine.Actor.CUSTOMER, userId, "Return requested by customer");
		return toDetail(reload(orderId));
	}

	/* --- admin action ------------------------------------------------------- */

	/** Validates the transition against the state machine and its actor rules. */
	@Transactional
	public OrderDetailResponse changeStatusByAdmin(String orderId,
			UpdateOrderStatusRequest request) {
		Status to = OrderStateMachine.parse(request.status());
		String note = normalizeNote(request.note());
		orderStatusService.transition(orderId, to, OrderStateMachine.Actor.ADMIN, null,
			note == null ? "Changed by administrator" : note);
		return toDetail(reload(orderId));
	}

	/* --- helpers ------------------------------------------------------------ */

	private void requireOwned(String orderId, String userId) {
		// Ownership and the right to see the order are the same check: a customer
		// must not be able to tell an existing order of someone else from a missing one.
		orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(OrderService::notFound);
	}

	private OrderEntity reload(String orderId) {
		return orderRepository.findById(orderId).orElseThrow(OrderService::notFound);
	}

	private OrderDetailResponse toDetail(OrderEntity order) {
		List<OrderItemEntity> items = orderItemRepository
			.findByOrderIdOrderByCreatedAtAsc(order.getId());
		List<OrderStatusHistoryResponse> history = historyRepository
			.findByOrderIdOrderByCreatedAtAsc(order.getId())
			.stream()
			.map(row -> new OrderStatusHistoryResponse(
				row.getFromStatus() == null ? null : row.getFromStatus().name(),
				row.getToStatus().name(),
				row.getNote(),
				row.getCreatedAt()))
			.toList();

		OrderAddressResponse address = new OrderAddressResponse(
			order.getShipRecipientName(),
			order.getShipPhone(),
			order.getShipAddressLine1(),
			order.getShipAddressLine2(),
			order.getShipWard(),
			order.getShipDistrict(),
			order.getShipCityOrProvince(),
			order.getShipPostalCode());

		return new OrderDetailResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getStatus().name(),
			order.getPaymentStatus().name(),
			order.getCurrency(),
			order.getSubtotal(),
			order.getDiscountTotal(),
			order.getShippingTotal(),
			order.getGrandTotal(),
			order.getShippingMethodCode(),
			order.getShippingMethodName(),
			address,
			order.getCustomerNote(),
			order.getPlacedAt(),
			items.stream().map(OrderService::toItem).toList(),
			history);
	}

	static OrderItemResponse toItem(OrderItemEntity item) {
		return new OrderItemResponse(
			item.getId(),
			item.getVariantId(),
			item.getProductNameSnapshot(),
			item.getSkuSnapshot(),
			item.getSizeSnapshot(),
			item.getColorSnapshot(),
			item.getImageUrlSnapshot(),
			item.getUnitPrice(),
			item.getQuantity(),
			item.getLineTotal());
	}

	private Map<String, Integer> itemCounts(List<String> orderIds) {
		if (orderIds.isEmpty()) {
			return Map.of();
		}
		Map<String, Integer> counts = new HashMap<>();
		for (OrderItemEntity item : orderItemRepository
			.findByOrderIdInOrderByCreatedAtAsc(orderIds)) {
			counts.merge(item.getOrderId(), item.getQuantity(), Integer::sum);
		}
		return counts;
	}

	private PageResponse<OrderSummaryResponse> toPage(Page<OrderEntity> orders,
			PageRequest pageable) {
		Map<String, Integer> itemCounts = itemCounts(
			orders.getContent().stream().map(OrderEntity::getId).toList());
		List<OrderSummaryResponse> rows = orders.getContent().stream()
			.map(order -> new OrderSummaryResponse(
				order.getId(),
				order.getOrderNumber(),
				order.getStatus().name(),
				order.getPaymentStatus().name(),
				order.getCurrency(),
				order.getGrandTotal(),
				itemCounts.getOrDefault(order.getId(), 0),
				order.getPlacedAt()))
			.toList();
		return PageResponse.of(rows, pageable.getPageNumber(), pageable.getPageSize(),
			orders.getTotalElements());
	}


	private static void requireUser(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
	}

	private static ApiException notFound() {
		return new ApiException.NotFound("Order not found");
	}

	static int validatePage(int page) {
		if (page < 0) {
			throw new ApiException.Validation("Page must be zero or greater",
				Map.of("page", "Must be 0 or greater"));
		}
		return page;
	}

	static int validateSize(int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException.Validation("Page size must be between 1 and " + MAX_PAGE_SIZE,
				Map.of("size", "Must be between 1 and " + MAX_PAGE_SIZE));
		}
		return size;
	}

	private static String normalizeNote(String note) {
		if (note == null || note.isBlank()) {
			return null;
		}
		String trimmed = note.trim();
		if (trimmed.length() > MAX_NOTE_LENGTH) {
			throw new ApiException.Validation("Note is too long",
				Map.of("note", "Must be at most " + MAX_NOTE_LENGTH + " characters"));
		}
		return trimmed;
	}
}

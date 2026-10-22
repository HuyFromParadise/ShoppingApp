package clothing.app.domain.order;

import java.security.MessageDigest;import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.cart.CartItemRepository;
import clothing.app.domain.checkout.CheckoutService;
import clothing.app.domain.checkout.PricedCheckout;
import clothing.app.domain.checkout.ShippingStrategy;
import clothing.app.domain.inventory.InventoryService;
import clothing.app.domain.order.dto.CreateOrderRequest;
import clothing.app.domain.order.dto.OrderAddressResponse;
import clothing.app.domain.order.dto.OrderItemResponse;
import clothing.app.domain.order.dto.OrderResponse;
import clothing.app.domain.payment.IdempotencyRecordEntity;
import clothing.app.domain.payment.IdempotencyRecordRepository;
import clothing.app.domain.payment.PaymentEntity;
import clothing.app.domain.payment.PaymentRepository;
import clothing.app.domain.user.UserRepository;

/**
 * Order creation (DESIGN_REFERENCE §8): one transaction that prices the cart,
 * decrements stock atomically, snapshots the purchase and clears the cart.
 *
 * <p>The {@code Idempotency-Key} header makes retries safe: a completed key
 * replays the stored response, a reused key with a different request is a 409,
 * and a failed attempt stores nothing, so the customer can retry after fixing
 * the cart.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OrderService {

	private static final String ORDER_NUMBER_PREFIX = "ORD-";
	/** Crockford base32: no I, L, O or U, so numbers survive being read aloud. */
	private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final int ORDER_NUMBER_RANDOM_LENGTH = 8;
	private static final int CREATED_STATUS = 201;
	/** ADR-007: the mock gateway is the only enabled provider until VNPAY_* is set. */
	private static final String PAYMENT_PROVIDER = "MOCK";
	private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,80}");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final CheckoutService checkoutService;
	private final InventoryService inventoryService;
	private final CartItemRepository cartItemRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderStatusHistoryRepository historyRepository;
	private final PaymentRepository paymentRepository;
	private final IdempotencyRecordRepository idempotencyRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public OrderService(
		CheckoutService checkoutService,
		InventoryService inventoryService,
		CartItemRepository cartItemRepository,
		OrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		OrderStatusHistoryRepository historyRepository,
		PaymentRepository paymentRepository,
		IdempotencyRecordRepository idempotencyRepository,
		UserRepository userRepository,
		ObjectMapper objectMapper) {
		this.checkoutService = checkoutService;
		this.inventoryService = inventoryService;
		this.cartItemRepository = cartItemRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.historyRepository = historyRepository;
		this.paymentRepository = paymentRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
	}

	public OrderResponse create(String userId, String idempotencyKey,
			CreateOrderRequest request) {
		requireUser(userId);
		String key = requireIdempotencyKey(idempotencyKey);
		String requestHash = hashOf(request);

		OrderResponse replayed = replay(userId, key, requestHash);
		if (replayed != null) {
			return replayed;
		}

		// The cart's per-customer mutex (CartService). It also serialises two
		// retries of this key, so only one of them can create the order.
		userRepository.findByIdForUpdate(userId)
			.orElseThrow(() -> new ApiException.Unauthenticated("Authentication required"));

		replayed = replay(userId, key, requestHash);
		if (replayed != null) {
			return replayed;
		}

		PricedCheckout checkout = checkoutService.price(userId, request.addressId(),
			request.shippingMethod(), request.promotionCode(), true);
		for (PricedCheckout.Line line : checkout.lines()) {
			inventoryService.decrement(line.variant().getId(), line.quantity());
		}

		OrderEntity order = saveOrder(userId, key, request, checkout);
		List<OrderItemEntity> items = saveItems(order, checkout);
		saveHistory(order, userId);
		savePayment(order);
		cartItemRepository.deleteByCartId(checkout.cartId());

		OrderResponse response = toResponse(order, items);
		idempotencyRepository.save(
			record(userId, key, requestHash, writeResponse(response)));
		return response;
	}

	/* --- persistence -------------------------------------------------------- */

	private OrderEntity saveOrder(String userId, String key, CreateOrderRequest request,
			PricedCheckout checkout) {
		AddressEntity address = checkout.address();
		ShippingStrategy.Method shipping = checkout.shippingMethod();

		OrderEntity order = new OrderEntity();
		order.setOrderNumber(newOrderNumber());
		order.setUserId(userId);
		order.setStatus(OrderEntity.Status.PENDING_PAYMENT);
		order.setPaymentStatus(OrderEntity.PaymentStatus.UNPAID);
		order.setSubtotal(checkout.subtotalMinor());
		order.setDiscountTotal(checkout.discountTotalMinor());
		order.setShippingTotal(checkout.shippingTotalMinor());
		order.setGrandTotal(checkout.grandTotalMinor());
		order.setCurrency(checkout.currency());

		// Snapshot the address: editing the address book later must not rewrite
		// an order that was already placed.
		order.setShipRecipientName(address.getRecipientName());
		order.setShipPhone(address.getPhone());
		order.setShipAddressLine1(address.getAddressLine1());
		order.setShipAddressLine2(address.getAddressLine2());
		order.setShipWard(address.getWard());
		order.setShipDistrict(address.getDistrict());
		order.setShipCityOrProvince(address.getCityOrProvince());
		order.setShipPostalCode(address.getPostalCode());

		order.setShippingMethodCode(shipping.code());
		order.setShippingMethodName(shipping.name());
		order.setCustomerNote(normalizeNote(request.customerNote()));
		order.setIdempotencyKey(key);
		order.setPlacedAt(Instant.now());
		return orderRepository.save(order);
	}

	private List<OrderItemEntity> saveItems(OrderEntity order, PricedCheckout checkout) {
		List<OrderItemEntity> items = checkout.lines().stream().map(line -> {
			OrderItemEntity item = new OrderItemEntity();
			item.setOrderId(order.getId());
			item.setVariantId(line.variant().getId());
			item.setProductNameSnapshot(line.product().getName());
			item.setSkuSnapshot(line.variant().getSku());
			item.setSizeSnapshot(line.variant().getSize());
			item.setColorSnapshot(line.variant().getColor());
			item.setImageUrlSnapshot(line.imageUrl());
			item.setUnitPrice(line.unitPrice());
			item.setQuantity(line.quantity());
			item.setLineTotal(line.lineTotal());
			return item;
		}).toList();
		return orderItemRepository.saveAll(items);
	}

	private void saveHistory(OrderEntity order, String actorUserId) {
		OrderStatusHistoryEntity history = new OrderStatusHistoryEntity();
		history.setOrderId(order.getId());
		history.setFromStatus(null);
		history.setToStatus(OrderEntity.Status.PENDING_PAYMENT);
		history.setActorUserId(actorUserId);
		history.setNote("Order placed");
		historyRepository.save(history);
	}

	private void savePayment(OrderEntity order) {
		PaymentEntity payment = new PaymentEntity();
		payment.setOrderId(order.getId());
		payment.setProvider(PAYMENT_PROVIDER);
		payment.setAmount(order.getGrandTotal());
		payment.setCurrency(order.getCurrency());
		payment.setStatus(PaymentEntity.Status.PENDING);
		paymentRepository.save(payment);
	}

	/* --- idempotency -------------------------------------------------------- */

	/** A completed attempt with the same key and request, or null. */
	private OrderResponse replay(String userId, String key, String requestHash) {
		return idempotencyRepository.findByUserIdAndIdempotencyKey(userId, key)
			.map(record -> {
				if (!record.getRequestHash().equals(requestHash)) {
					throw new ApiException.IdempotencyKeyReused(
						"This idempotency key was already used with a different request");
				}
				return readResponse(record);
			})
			.orElse(null);
	}

	private IdempotencyRecordEntity record(String userId, String key, String requestHash,
			String responseBody) {
		IdempotencyRecordEntity record = new IdempotencyRecordEntity();
		record.setUserId(userId);
		record.setIdempotencyKey(key);
		record.setRequestHash(requestHash);
		record.setStatusCode(CREATED_STATUS);
		record.setResponseBody(responseBody);
		return record;
	}

	private String hashOf(CreateOrderRequest request) {
		try {
			byte[] json = objectMapper.writeValueAsBytes(request);
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(json));
		}
		catch (JsonProcessingException | NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Could not hash the order request", ex);
		}
	}

	private String writeResponse(OrderResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not store the order response", ex);
		}
	}

	private OrderResponse readResponse(IdempotencyRecordEntity record) {
		try {
			return objectMapper.readValue(record.getResponseBody(), OrderResponse.class);
		}
		catch (JsonProcessingException | IllegalArgumentException ex) {
			throw new IllegalStateException("Stored idempotent response is unreadable", ex);
		}
	}

	/* --- rules and response -------------------------------------------------- */

	private static void requireUser(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
	}

	private static String requireIdempotencyKey(String key) {
		if (key == null || key.isBlank()) {
			throw new ApiException.Validation("Idempotency-Key header is required",
				Map.of("Idempotency-Key", "Required"));
		}
		String trimmed = key.trim();
		if (!IDEMPOTENCY_KEY.matcher(trimmed).matches()) {
			throw new ApiException.Validation("Idempotency-Key is invalid",
				Map.of("Idempotency-Key",
					"Must be 8-80 characters of A-Z, a-z, 0-9, _ or -"));
		}
		return trimmed;
	}

	private static String newOrderNumber() {
		StringBuilder number = new StringBuilder(ORDER_NUMBER_PREFIX);
		for (int i = 0; i < ORDER_NUMBER_RANDOM_LENGTH; i++) {
			number.append(CROCKFORD.charAt(RANDOM.nextInt(CROCKFORD.length())));
		}
		return number.toString();
	}

	private static String normalizeNote(String note) {
		return note == null || note.isBlank() ? null : note.trim();
	}

	private static OrderResponse toResponse(OrderEntity order, List<OrderItemEntity> items) {
		OrderAddressResponse address = new OrderAddressResponse(
			order.getShipRecipientName(),
			order.getShipPhone(),
			order.getShipAddressLine1(),
			order.getShipAddressLine2(),
			order.getShipWard(),
			order.getShipDistrict(),
			order.getShipCityOrProvince(),
			order.getShipPostalCode());

		List<OrderItemResponse> lines = items.stream()
			.map(item -> new OrderItemResponse(
				item.getId(),
				item.getVariantId(),
				item.getProductNameSnapshot(),
				item.getSkuSnapshot(),
				item.getSizeSnapshot(),
				item.getColorSnapshot(),
				item.getImageUrlSnapshot(),
				item.getUnitPrice(),
				item.getQuantity(),
				item.getLineTotal()))
			.toList();

		return new OrderResponse(
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
			lines);
	}
}

package clothing.app.domain.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.order.OrderEntity;
import clothing.app.domain.order.OrderEntity.PaymentStatus;
import clothing.app.domain.order.OrderRepository;
import clothing.app.domain.order.OrderStateMachine;
import clothing.app.domain.order.OrderStatusService;
import clothing.app.domain.order.dto.PaymentInitiationResponse;
import clothing.app.domain.order.dto.PaymentResultResponse;
import clothing.app.domain.payment.dto.PaymentStatusView;

/**
 * Payment lifecycle (DESIGN_REFERENCE §7, ADR-007).
 *
 * <p>Payment status is deliberately separate from fulfillment: a settled
 * payment moves {@code payment_status} to {@code PAID} and the order to
 * {@code CONFIRMED} exactly once, and a failed attempt records
 * {@code FAILED} without touching the order, which can still be retried or
 * cancelled. Every entry point here is idempotent, because a webhook may be
 * delivered more than once and the customer may refresh the return page.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final OrderStatusService orderStatusService;
	private final MockPaymentGateway mockGateway;
	private final VnPayGateway vnPayGateway;

	public PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
			PaymentEventRepository paymentEventRepository,
			OrderStatusService orderStatusService, MockPaymentGateway mockGateway,
			VnPayGateway vnPayGateway) {
		this.orderRepository = orderRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.orderStatusService = orderStatusService;
		this.mockGateway = mockGateway;
		this.vnPayGateway = vnPayGateway;
	}

	/* --- customer entry points --------------------------------------------- */

	/**
	 * Starts a VNPay payment. Unconfigured credentials are a 503
	 * {@code PROVIDER_NOT_CONFIGURED}; nothing is stored in that case.
	 */
	@Transactional
	public PaymentInitiationResponse createVnPay(String userId, String orderId, String clientIp,
			String returnUrl) {
		OrderEntity order = requireOwnedPayable(userId, orderId);
		PaymentGateway.PaymentInitiation initiation = vnPayGateway.create(
			new PaymentGateway.PaymentRequest(order.getId(), order.getOrderNumber(),
				order.getGrandTotal(), order.getCurrency(), clientIp, returnUrl));
		return start(order, initiation);
	}

	/**
	 * Starts the development payment. It is unmistakably a mock in the response
	 * ({@code mock: true}) and never reaches a real provider.
	 */
	@Transactional
	public PaymentInitiationResponse createMock(String userId, String orderId, String clientIp) {
		OrderEntity order = requireOwnedPayable(userId, orderId);
		PaymentGateway.PaymentInitiation initiation = mockGateway.create(
			new PaymentGateway.PaymentRequest(order.getId(), order.getOrderNumber(),
				order.getGrandTotal(), order.getCurrency(), clientIp, null));
		return start(order, initiation);
	}

	/**
	 * Settles a mock payment from the local confirmation page. Only the mock
	 * provider and only its own payment id are accepted, so this can never be
	 * used to mark a VNPay order paid.
	 */
	@Transactional
	public PaymentResultResponse confirmMock(String userId, String paymentId, boolean approve) {
		PaymentEntity payment = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new ApiException.NotFound("Payment not found"));
		if (!MockPaymentGateway.PROVIDER.equals(payment.getProvider())) {
			throw new ApiException.Forbidden("This payment cannot be confirmed from the storefront");
		}
		OrderEntity order = requireOwnedForUpdate(userId, payment.getOrderId());
		return settle(order, payment, approve, MockPaymentGateway.PROVIDER,
			"mock-confirm-" + payment.getId());
	}

	/** The customer's view of an order's payments. */
	@Transactional(readOnly = true)
	public List<PaymentStatusView> listOwn(String userId, String orderId) {
		requireOwned(userId, orderId);
		return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
			.map(payment -> new PaymentStatusView(payment.getId(), payment.getProvider(),
				payment.getStatus().name(), payment.getAmount(), payment.getCurrency(),
				payment.getProviderTransactionId(), payment.getCreatedAt()))
			.toList();
	}

	/* --- webhooks ----------------------------------------------------------- */

	/**
	 * VNPay webhook/return callback. The signature is verified before anything is
	 * read, and the event id is recorded so a replay of the same event is a no-op
	 * (DESIGN_REFERENCE §4 {@code payment_events}, PROJECT_SPEC §14).
	 */
	@Transactional
	public PaymentResultResponse handleVnPayCallback(Map<String, String> params) {
		VnPayGateway.Verification verification = vnPayGateway.verify(params);
		String eventId = eventId("VNPAY", verification.txnRef(), verification.transactionNo(),
			verification.responseCode(), verification.transactionStatus());
		if (alreadyProcessed(VnPayGateway.PROVIDER, eventId)) {
			log.info("Ignoring duplicate VNPay event {}", eventId);
			return replayResult(verification.txnRef());
		}

		OrderEntity found = orderRepository
			.findByOrderNumber(verification.txnRef())
			.orElseThrow(() -> new ApiException.NotFound("Order not found"));
		// Take the order lock before touching the payments, in the same order every
		// other settle path uses, so two confirmations cannot interleave.
		OrderEntity order = orderRepository.findByIdForUpdate(found.getId())
			.orElseThrow(() -> new ApiException.NotFound("Order not found"));
		// The callback names the provider that actually took the money, so the
		// stored attempt follows it: an order created with the placeholder mock row
		// that was then paid through VNPay is settled as a VNPay payment.
		PaymentEntity payment = paymentRepository
			.findAllForUpdateByOrderId(order.getId()).stream()
			.findFirst()
			.orElseGet(() -> newPayment(order, VnPayGateway.PROVIDER, verification.txnRef()));
		payment.setProvider(VnPayGateway.PROVIDER);
		payment.setProviderTransactionId(verification.txnRef());
		recordEvent(VnPayGateway.PROVIDER, eventId,
			payloadHash(params, verification.transactionNo()));

		return settle(order, payment, verification.success(), VnPayGateway.PROVIDER, eventId);
	}

	/* --- shared lifecycle --------------------------------------------------- */

	private PaymentInitiationResponse start(OrderEntity order,
			PaymentGateway.PaymentInitiation initiation) {
		PaymentEntity payment = paymentRepository
			.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
			.filter(candidate -> candidate.getStatus() == PaymentEntity.Status.PENDING)
			.orElseGet(() -> newPayment(order, initiation.provider(),
				initiation.providerTransactionId()));
		payment.setProvider(initiation.provider());
		payment.setProviderTransactionId(initiation.providerTransactionId());
		payment = paymentRepository.save(payment);

		if (order.getPaymentStatus() == PaymentStatus.UNPAID) {
			order.setPaymentStatus(PaymentStatus.PENDING);
			orderRepository.save(order);
		}
		return new PaymentInitiationResponse(payment.getId(), order.getId(),
			initiation.provider(), payment.getStatus().name(),
			payment.getProviderTransactionId(), initiation.paymentUrl(),
			MockPaymentGateway.PROVIDER.equals(initiation.provider()), payment.getCreatedAt());
	}

	/**
	 * Applies the outcome of one attempt. A success is only honoured while the
	 * order still waits for payment, so a late webhook for a cancelled (timed out
	 * or cancelled) order cannot resurrect it.
	 */
	private PaymentResultResponse settle(OrderEntity order, PaymentEntity payment, boolean paid,
			String provider, String eventId) {
		Instant now = Instant.now();
		if (paid && order.getPaymentStatus() != PaymentStatus.PAID
				&& order.getStatus() == OrderEntity.Status.PENDING_PAYMENT) {
			payment.setStatus(PaymentEntity.Status.PAID);
			order.setPaymentStatus(PaymentStatus.PAID);
			orderRepository.save(order);
			orderStatusService.applyTransition(order, OrderEntity.Status.CONFIRMED,
				OrderStateMachine.Actor.SYSTEM, null, "Payment confirmed");
		}
		else if (!paid && payment.getStatus() != PaymentEntity.Status.PAID) {
			payment.setStatus(PaymentEntity.Status.FAILED);
			order.setPaymentStatus(PaymentStatus.FAILED);
			orderRepository.save(order);
		}
		else if (paid) {
			// Already paid: the callback is a duplicate, so keep the outcome stable
			// and do not move the order a second time.
			payment.setStatus(PaymentEntity.Status.PAID);
		}
		payment.setUpdatedAt(now);
		paymentRepository.save(payment);
		log.info("Payment {} for order {} settled as {} (event {})", payment.getId(),
			order.getId(), paid ? "PAID" : "FAILED", eventId);

		return new PaymentResultResponse(order.getId(), order.getOrderNumber(),
			order.getStatus().name(), order.getPaymentStatus().name(), provider, paid, now);
	}

	private PaymentResultResponse replayResult(String orderNumber) {
		OrderEntity order = orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new ApiException.NotFound("Order not found"));
		boolean paid = order.getPaymentStatus() == PaymentStatus.PAID;
		return new PaymentResultResponse(order.getId(), order.getOrderNumber(),
			order.getStatus().name(), order.getPaymentStatus().name(), VnPayGateway.PROVIDER,
			paid, order.getUpdatedAt());
	}

	private PaymentEntity newPayment(OrderEntity order, String provider, String transactionId) {
		PaymentEntity payment = new PaymentEntity();
		payment.setOrderId(order.getId());
		payment.setProvider(provider);
		payment.setProviderTransactionId(transactionId);
		payment.setAmount(order.getGrandTotal());
		payment.setCurrency(order.getCurrency());
		payment.setStatus(PaymentEntity.Status.PENDING);
		return payment;
	}

	private boolean alreadyProcessed(String provider, String eventId) {
		return paymentEventRepository.existsByProviderAndProviderEventId(provider, eventId);
	}

	private void recordEvent(String provider, String eventId, String payloadSha256) {
		PaymentEventEntity event = new PaymentEventEntity();
		event.setProvider(provider);
		event.setProviderEventId(eventId);
		event.setPayloadSha256(payloadSha256);
		event.setProcessedAt(Instant.now());
		paymentEventRepository.save(event);
	}

	private OrderEntity requireOwned(String userId, String orderId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
		return orderRepository.findByIdAndUserId(orderId, userId)
			.orElseThrow(() -> new ApiException.NotFound("Order not found"));
	}

	private OrderEntity requireOwnedForUpdate(String userId, String orderId) {
		requireOwned(userId, orderId);
		return orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new ApiException.NotFound("Order not found"));
	}

	private OrderEntity requireOwnedPayable(String userId, String orderId) {
		OrderEntity order = requireOwned(userId, orderId);
		if (order.getStatus() != OrderEntity.Status.PENDING_PAYMENT) {
			throw new ApiException.InvalidStateTransition(
				"This order is not waiting for payment");
		}
		if (order.getPaymentStatus() == PaymentStatus.PAID) {
			throw new ApiException.Conflict("This order is already paid");
		}
		return order;
	}

	/**
	 * A stable dedupe key for a provider callback: the same event delivered twice
	 * produces the same id, a different outcome does not.
	 */
	static String eventId(String provider, String txnRef, String transactionNo,
			String responseCode, String transactionStatus) {
		String raw = String.join("|",
			provider == null ? "" : provider,
			txnRef == null ? "" : txnRef,
			transactionNo == null ? "" : transactionNo,
			responseCode == null ? "" : responseCode,
			transactionStatus == null ? "" : transactionStatus);
		return "EVT-" + sha256Hex(raw);
	}

	/** SHA-256 of the signed parameters, for audit without storing customer data. */
	static String payloadHash(Map<String, String> params, String transactionNo) {
		Map<String, String> sorted = new TreeMap<>(params);
		sorted.remove("vnp_SecureHash");
		StringBuilder canonical = new StringBuilder();
		sorted.forEach((key, value) -> canonical.append(key).append('=').append(value)
			.append('&'));
		if (transactionNo != null) {
			canonical.append("txn=").append(transactionNo);
		}
		return sha256Hex(canonical.toString());
	}

	static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}

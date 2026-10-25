package clothing.app.domain.payment;

import java.util.Map;

/**
 * A payment provider behind an interface (PROJECT_SPEC §14). Nothing outside
 * this package knows which provider is enabled.
 */
public interface PaymentGateway {

	String provider();

	/** Whether the provider can take payments right now; absent credentials mean no. */
	boolean isConfigured();

	/**
	 * Starts a payment for an order. Throws
	 * {@link clothing.app.common.exception.ApiException.ProviderNotConfigured}
	 * when the provider has no credentials, so the customer never sees a fake
	 * "pay now" flow.
	 */
	PaymentInitiation create(PaymentRequest request);

	/** A payment attempt: the order to charge and what it costs. */
	record PaymentRequest(
			String orderId,
			String orderNumber,
			long amountMinor,
			String currency,
			String clientIp,
			String returnUrl) {
	}

	/**
	 * Where to send the customer, and the provider reference to store. For the
	 * mock gateway the URL is a local, clearly-labelled confirmation page.
	 */
	record PaymentInitiation(
			String provider,
			String providerTransactionId,
			String paymentUrl,
			Map<String, String> metadata) {
	}
}

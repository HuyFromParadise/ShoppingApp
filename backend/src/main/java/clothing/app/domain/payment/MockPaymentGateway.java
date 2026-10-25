package clothing.app.domain.payment;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * The development adapter (PROJECT_SPEC §14, ADR-007). It never moves money and
 * says so in every field it returns, so the flow can be exercised end to end
 * without pretending to be a real provider.
 *
 * <p>The customer is sent to a local confirmation page that calls
 * {@code POST /payments/mock/{paymentId}/confirm}.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

	public static final String PROVIDER = "MOCK";
	static final String PAYMENT_PATH = "/payments/mock/";

	private final String publicBaseUrl;

	public MockPaymentGateway(
			@org.springframework.beans.factory.annotation.Value("${app.payment.public-base-url:}")
			String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public boolean isConfigured() {
		return true;
	}

	@Override
	public PaymentInitiation create(PaymentRequest request) {
		String transactionId = "MOCK-" + UUID.randomUUID().toString().replace("-", "");
		return new PaymentInitiation(
			PROVIDER,
			transactionId,
			publicBaseUrl + PAYMENT_PATH + transactionId + "/confirm",
			Map.of("mode", "DEVELOPMENT",
				"notice", "Mock payment: no money is charged"));
	}
}

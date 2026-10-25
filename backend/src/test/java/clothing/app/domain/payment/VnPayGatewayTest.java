package clothing.app.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import clothing.app.common.exception.ApiException;
import clothing.app.common.exception.ErrorCode;

/**
 * VNPay skeleton contract (ADR-007): unconfigured means unusable, and a
 * configured instance builds and verifies the provider's signed query string.
 * No real credentials are involved.
 */
class VnPayGatewayTest {

	private static final String SECRET = "unit-test-secret";

	@Test
	void isNotConfiguredWithoutCredentials() {
		VnPayGateway gateway = new VnPayGateway("", null, "  ", 15);
		assertThat(gateway.isConfigured()).isFalse();
		assertThatThrownBy(() -> gateway.create(new PaymentGateway.PaymentRequest("o1", "ORD-1",
			1000L, "VND", "127.0.0.1", "http://localhost/return")))
			.isInstanceOf(ApiException.ProviderNotConfigured.class)
			.extracting(ex -> ((ApiException) ex).getErrorCode())
			.isEqualTo(ErrorCode.PROVIDER_NOT_CONFIGURED);
		assertThatThrownBy(() -> gateway.verify(Map.of("vnp_TxnRef", "ORD-1")))
			.isInstanceOf(ApiException.ProviderNotConfigured.class);
	}

	@Test
	void requiresAllThreeSettingsToBeConfigured() {
		assertThat(new VnPayGateway("TMN", SECRET, "", 15).isConfigured()).isFalse();
		assertThat(new VnPayGateway("TMN", "", "https://pay", 15).isConfigured()).isFalse();
		assertThat(new VnPayGateway("TMN", SECRET, "https://pay", 15).isConfigured()).isTrue();
	}

	@Test
	void buildsASignedPaymentUrl() {
		VnPayGateway gateway = new VnPayGateway("TMN", SECRET, "https://pay", 15);
		PaymentGateway.PaymentInitiation initiation = gateway.create(
			new PaymentGateway.PaymentRequest("o1", "ORD-ABCD1234", 529000L, "VND",
				"203.0.113.7", "http://localhost:3000/account/orders"));

		assertThat(initiation.provider()).isEqualTo("VNPAY");
		assertThat(initiation.providerTransactionId()).isEqualTo("ORD-ABCD1234");
		assertThat(initiation.paymentUrl()).startsWith("https://pay?")
			.contains("vnp_TxnRef=ORD-ABCD1234")
			.contains("vnp_Amount=529000")
			.contains("vnp_IpAddr=203.0.113.7")
			.contains("vnp_SecureHash=");

		String query = initiation.paymentUrl().substring("https://pay?".length());
		String body = query.substring(0, query.indexOf("&vnp_SecureHash="));
		String signature = query.substring(query.indexOf("&vnp_SecureHash=")
			+ "&vnp_SecureHash=".length());
		assertThat(signature).isEqualTo(VnPayGateway.hmacSha512(SECRET, body));
	}

	@Test
	void verifiesAGenuineCallbackAndRejectsATamperedOne() {
		VnPayGateway gateway = new VnPayGateway("TMN", SECRET, "https://pay", 15);
		Map<String, String> params = Map.of(
			"vnp_TxnRef", "ORD-ABCD1234",
			"vnp_TransactionNo", "14123456",
			"vnp_ResponseCode", "00",
			"vnp_TransactionStatus", "00");
		String signature = VnPayGateway.hmacSha512(SECRET,
			VnPayGateway.canonicalQuery(params));

		java.util.Map<String, String> callback = new java.util.HashMap<>(params);
		callback.put("vnp_SecureHash", signature);
		VnPayGateway.Verification verification = gateway.verify(callback);
		assertThat(verification.success()).isTrue();
		assertThat(verification.txnRef()).isEqualTo("ORD-ABCD1234");
		assertThat(verification.transactionNo()).isEqualTo("14123456");

		callback.put("vnp_ResponseCode", "24");
		assertThatThrownBy(() -> gateway.verify(callback))
			.isInstanceOf(ApiException.Forbidden.class);
	}

	@Test
	void treatsANonZeroResponseCodeAsFailure() {
		VnPayGateway gateway = new VnPayGateway("TMN", SECRET, "https://pay", 15);
		Map<String, String> params = Map.of(
			"vnp_TxnRef", "ORD-ABCD1234",
			"vnp_TransactionNo", "14123456",
			"vnp_ResponseCode", "24",
			"vnp_TransactionStatus", "02");
		java.util.Map<String, String> callback = new java.util.HashMap<>(params);
		callback.put("vnp_SecureHash",
			VnPayGateway.hmacSha512(SECRET, VnPayGateway.canonicalQuery(params)));

		assertThat(gateway.verify(callback).success()).isFalse();
	}

	@Test
	void requiresASignature() {
		VnPayGateway gateway = new VnPayGateway("TMN", SECRET, "https://pay", 15);
		assertThatThrownBy(() -> gateway.verify(Map.of("vnp_TxnRef", "ORD-1")))
			.isInstanceOf(ApiException.Forbidden.class)
			.hasMessageContaining("signature");
	}

	@Test
	void canonicalQueryIsSortedAndPercentEncoded() {
		Map<String, String> params = new java.util.LinkedHashMap<>();
		params.put("b", "two words");
		params.put("a", "1");
		params.put("empty", "");
		assertThat(VnPayGateway.canonicalQuery(params)).isEqualTo("a=1&b=two%20words");
	}
}

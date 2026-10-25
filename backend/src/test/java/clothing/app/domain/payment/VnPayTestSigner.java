package clothing.app.domain.payment;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Signs a VNPay callback for the tests the same way the gateway verifies it:
 * sorted, URL-encoded parameters plus an HMAC-SHA512 of that string.
 *
 * <p>The secret is a throwaway value from the test's properties, not a real
 * credential; the algorithm is the provider's published v2.1.0 one.
 */
public final class VnPayTestSigner {

	public static final String SECRET = "integration-test-only-secret";

	private VnPayTestSigner() {
	}

	public static Map<String, String> sign(Map<String, String> params) {
		Map<String, String> signed = new LinkedHashMap<>(params);
		signed.put("vnp_SecureHash",
			VnPayGateway.hmacSha512(SECRET, VnPayGateway.canonicalQuery(params)));
		return signed;
	}

	public static MultiValueMap<String, String> params(Map<String, String> params) {
		MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
		params.forEach(values::add);
		return values;
	}
}

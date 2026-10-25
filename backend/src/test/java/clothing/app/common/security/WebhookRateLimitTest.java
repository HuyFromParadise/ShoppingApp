package clothing.app.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The payment webhooks are unauthenticated from a session point of view, so a
 * forged or replayed callback must not be able to churn through the signature
 * check for free (DESIGN_REFERENCE §6.3): the per-IP bucket applies to them too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=3",
	"app.orders.timeout-sweep-interval-ms=86400000",
	"VNPAY_TMN_CODE=TESTTMN", "VNPAY_HASH_SECRET=integration-test-only-secret",
	"VNPAY_PAY_URL=https://sandbox.invalid/pay"})
class WebhookRateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void throttlesRepeatedWebhookCalls() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
					.contentType("application/x-www-form-urlencoded")
					.param("vnp_TxnRef", "ORD-NOPE")
					.param("vnp_ResponseCode", "24"))
				.andExpect(status().isForbidden());
		}
		mockMvc.perform(post("/api/v1/payments/webhooks/vnpay")
				.contentType("application/x-www-form-urlencoded")
				.param("vnp_TxnRef", "ORD-NOPE")
				.param("vnp_ResponseCode", "24"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}
}

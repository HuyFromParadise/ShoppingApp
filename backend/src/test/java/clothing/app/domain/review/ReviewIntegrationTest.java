package clothing.app.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.security.JwtService;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.cart.CartItemRepository;
import clothing.app.domain.cart.CartRepository;
import clothing.app.domain.order.OrderWorkflowFixture;
import clothing.app.domain.order.OrderWorkflowFixture.Customer;
import clothing.app.domain.user.UserRepository;

/**
 * Reviews (PROJECT_SPEC §7, P1) under the chosen policy: publish immediately,
 * one review per customer per product, and a verified badge derived from the
 * customer's own delivered purchase — never from anything they claim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"RATE_LIMIT_AUTH_CAPACITY=1000",
	"app.orders.timeout-sweep-interval-ms=86400000"})
@Transactional
class ReviewIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private AddressRepository addressRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductVariantRepository variantRepository;
	@Autowired
	private ProductImageRepository imageRepository;
	@Autowired
	private CartRepository cartRepository;
	@Autowired
	private CartItemRepository cartItemRepository;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private EntityManager entityManager;

	private OrderWorkflowFixture fixture;
	private String adminToken;

	@BeforeEach
	void setUp() {
		fixture = new OrderWorkflowFixture(mockMvc, jdbcTemplate, userRepository,
			passwordEncoder, addressRepository, categoryRepository, productRepository,
			variantRepository, imageRepository, jwtService, entityManager);
		adminToken = fixture.tokenOf(fixture.newAdmin("review-admin"));
	}

	@Test
	void publishingIsImmediateAndTheAuthorIsNotIdentifiable() throws Exception {
		Product product = productWithShop("publish");
		Customer reviewer = fixture.newCustomer("review-author", 1);

		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.header("Authorization", "Bearer " + fixture.tokenOf(reviewer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\":5,\"title\":\"Lovely\",\"comment\":\"Fits well\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.rating").value(5))
			.andExpect(jsonPath("$.data.title").value("Lovely"))
			// first name plus an initial only
			.andExpect(jsonPath("$.data.authorName").value("An N."))
			.andExpect(jsonPath("$.data.verifiedPurchase").value(false));

		mockMvc.perform(get("/api/v1/products/" + product.slug() + "/reviews"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reviewCount").value(1))
			.andExpect(jsonPath("$.data.averageRating").value(5.0))
			.andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	void aReviewerNeverAppearsWithTheirEmail() throws Exception {
		Product product = productWithShop("privacy");
		Customer reviewer = fixture.newCustomer("review-privacy", 1);

		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.header("Authorization", "Bearer " + fixture.tokenOf(reviewer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\":4}"))
			.andExpect(status().isCreated());

		String body = mockMvc.perform(get("/api/v1/products/" + product.slug() + "/reviews"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(reviewer.email().split("@")[0] + "@");
		assertThat(body).doesNotContain("userId");
	}

	@Test
	void aDeliveredPurchaseEarnsTheVerifiedBadge() throws Exception {
		Customer buyer = fixture.newCustomer("review-buyer", 2);
		String token = fixture.tokenOf(buyer);
		String productId = productIdOf(buyer.variantId());
		String slug = slugOf(productId);
		String orderId = fixture.placeOrder(token, buyer, "review-buyer-key", 1);
		deliverOrder(orderId, token);

		mockMvc.perform(post("/api/v1/products/" + slug + "/reviews")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\":5,\"comment\":\"Great\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.verifiedPurchase").value(true));
	}

	@Test
	void anUnpaidOrCancelledOrderDoesNotVerify() throws Exception {
		Customer buyer = fixture.newCustomer("review-unpaid", 2);
		String token = fixture.tokenOf(buyer);
		String slug = slugOf(productIdOf(buyer.variantId()));
		String orderId = fixture.placeOrder(token, buyer, "review-unpaid-key", 1);
		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/products/" + slug + "/reviews")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\":2}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.verifiedPurchase").value(false));
	}

	@Test
	void oneReviewPerCustomerPerProduct() throws Exception {
		Product product = productWithShop("duplicate");
		String token = fixture.tokenOf(fixture.newCustomer("review-dupe", 1));

		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void validatesTheRatingAndRequiresSignIn() throws Exception {
		Product product = productWithShop("validate");
		String token = fixture.tokenOf(fixture.newCustomer("review-invalid", 1));

		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
			.andExpect(status().isUnauthorized());

		for (String rating : new String[] {"0", "6", "-1"}) {
			mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"rating\":" + rating + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
		}

		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\":5,\"comment\":\"" + "x".repeat(2001) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.comment").isNotEmpty());
	}

	@Test
	void summarisesRatingCounts() throws Exception {
		Product product = productWithShop("summary");
		int[] ratings = {5, 5, 4, 3};
		for (int i = 0; i < ratings.length; i++) {
			String token = fixture.tokenOf(fixture.newCustomer("review-sum-" + i, 1));
			mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"rating\":" + ratings[i] + "}"))
				.andExpect(status().isCreated());
		}

		mockMvc.perform(get("/api/v1/products/" + product.slug() + "/reviews"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reviewCount").value(4))
			// (5+5+4+3)/4 = 4.25 → 4.3, one decimal
			.andExpect(jsonPath("$.data.averageRating").value(4.3))
			.andExpect(jsonPath("$.data.ratingCounts[4]").value(2))
			.andExpect(jsonPath("$.data.ratingCounts[3]").value(1))
			.andExpect(jsonPath("$.data.ratingCounts[2]").value(1))
			.andExpect(jsonPath("$.data.ratingCounts[0]").value(0));
	}

	@Test
	void reviewsOfAnArchivedProductAreNotPublic() throws Exception {
		Product product = productWithShop("archived");
		jdbcTemplate.update("update products set status = 'ARCHIVED' where id = ?", product.id());
		entityManager.clear();

		mockMvc.perform(get("/api/v1/products/" + product.slug() + "/reviews"))
			.andExpect(status().isNotFound());
	}

	@Test
	void eligibilityTellsThePageWhatToOffer() throws Exception {
		Product product = productWithShop("eligibility");

		mockMvc.perform(get("/api/v1/products/" + product.slug() + "/review-eligibility"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.signedIn").value(false))
			.andExpect(jsonPath("$.data.canReview").value(false));

		Customer customer = fixture.newCustomer("review-eligible", 1);
		String token = fixture.tokenOf(customer);
		mockMvc.perform(get("/api/v1/products/" + product.slug() + "/review-eligibility")
				.header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.signedIn").value(true))
			.andExpect(jsonPath("$.data.canReview").value(true))
			.andExpect(jsonPath("$.data.verifiedPurchase").value(false));

		mockMvc.perform(post("/api/v1/products/" + product.slug() + "/reviews")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/products/" + product.slug() + "/review-eligibility")
				.header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.canReview").value(false));
	}

	/* --- helpers -------------------------------------------------------------- */

	/** Moves an order to DELIVERED through the real payment and admin transitions. */
	private void deliverOrder(String orderId, String customerToken) throws Exception {
		mockMvc.perform(post("/api/v1/payments/" + orderId + "/mock/create")
				.header("Authorization", "Bearer " + customerToken))
			.andExpect(status().isOk());
		String paymentId = jdbcTemplate.queryForObject(
			"select id from payments where order_id = ? order by created_at limit 1",
			String.class, orderId);
		mockMvc.perform(post("/api/v1/payments/mock/" + paymentId + "/confirm")
				.header("Authorization", "Bearer " + customerToken))
			.andExpect(status().isOk());
		for (String next : new String[] {"PROCESSING", "SHIPPED", "DELIVERED"}) {
			mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
					.header("Authorization", "Bearer " + adminToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"" + next + "\"}"))
				.andExpect(status().isOk());
		}
	}
	private record Product(String id, String slug, String variantId) {
	}

	/** A product with an active category and one stocked variant, plus its slug. */
	private Product productWithShop(String hint) {
		Customer seeded = fixture.newCustomer(hint, 50);
		return new Product(productIdOf(seeded.variantId()), slugOf(productIdOf(seeded.variantId())),
			seeded.variantId());
	}

	private String productIdOf(String variantId) {
		return jdbcTemplate.queryForObject(
			"select product_id from product_variants where id = ?", String.class, variantId);
	}

	private String slugOf(String productId) {
		return jdbcTemplate.queryForObject("select slug from products where id = ?", String.class,
			productId);
	}
}

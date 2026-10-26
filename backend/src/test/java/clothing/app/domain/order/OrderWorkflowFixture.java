package clothing.app.domain.order;

import java.util.function.Function;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.security.JwtService;
import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.user.UserEntity;
import clothing.app.domain.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seeds a customer, an address and a one-variant active product through the real
 * repositories, then places orders through the public API. Shared by the order
 * workflow integration tests so each test starts from the same contract.
 */
public final class OrderWorkflowFixture {

	public static final long BASE_PRICE = 499000L;
	public static final long SHIPPING_FEE = 30000L;
	static final String PASSWORD = "Str0ngPass!234";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AddressRepository addressRepository;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final ProductVariantRepository variantRepository;
	private final ProductImageRepository imageRepository;
	private final Function<UserEntity, String> tokenIssuer;
	private final EntityManager entityManager;

	private int sequence;

	public OrderWorkflowFixture(MockMvc mockMvc, JdbcTemplate jdbcTemplate,
			UserRepository userRepository, PasswordEncoder passwordEncoder,
			AddressRepository addressRepository, CategoryRepository categoryRepository,
			ProductRepository productRepository, ProductVariantRepository variantRepository,
			ProductImageRepository imageRepository, JwtService jwtService,
			EntityManager entityManager) {
		this.mockMvc = mockMvc;
		this.jdbcTemplate = jdbcTemplate;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.addressRepository = addressRepository;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.variantRepository = variantRepository;
		this.imageRepository = imageRepository;
		this.entityManager = entityManager;
		// The same signing code the auth flow uses, so the token is accepted by the
		// real filter without running the refresh-cookie dance.
		this.tokenIssuer = user -> jwtService.generateAccessToken(user.getId(),
			user.getRole().name());
	}

	/** A new ACTIVE customer with one address and one stocked variant. */
	public Customer newCustomer(String slugHint, int stock) {
		return newCustomer(slugHint, stock, UserEntity.Role.CUSTOMER);
	}

	public Customer newAdmin(String slugHint) {
		return newCustomer(slugHint, 1, UserEntity.Role.ADMIN);
	}

	public String tokenOf(Customer customer) {
		UserEntity user = userRepository.findById(customer.id()).orElseThrow();
		return tokenIssuer.apply(user);
	}

	public String adminToken(Customer admin) {
		return tokenOf(admin);
	}

	public String registerCustomer(String slugHint) throws Exception {
		String email = slugHint + "-" + System.nanoTime() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
					+ "\"firstName\":\"An\",\"lastName\":\"Nguyen\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	public void addToCart(String token, String variantId, int quantity) throws Exception {
		mockMvc.perform(post("/api/v1/cart/items")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}"))
			.andExpect(status().isOk());
	}

	/** Places an order for {@code quantity} and returns the created order id. */
	public String placeOrder(String token, Customer customer, String key, int quantity)
			throws Exception {
		addToCart(token, customer.variantId(), quantity);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":\"" + customer.addressId() + "\","
					+ "\"shippingMethod\":\"STANDARD\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	public String orderNumber(String orderId) {
		flush();
		return jdbcTemplate.queryForObject("select order_number from orders where id = ?",
			String.class, orderId);
	}

	public int stockOf(String variantId) {
		flush();
		Integer stock = jdbcTemplate.queryForObject(
			"select stock_quantity from product_variants where id = ?", Integer.class, variantId);
		return stock == null ? -1 : stock;
	}

	public String statusOf(String orderId) {
		flush();
		return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class,
			orderId);
	}

	public String paymentStatusOf(String orderId) {
		flush();
		return jdbcTemplate.queryForObject("select payment_status from orders where id = ?",
			String.class, orderId);
	}

	String providerOf(String orderId) {
		flush();
		return jdbcTemplate.queryForObject(
			"select provider from payments where order_id = ? order by created_at limit 1",
			String.class, orderId);
	}

	public int historyCount(String orderId) {
		flush();
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from order_status_history where order_id = ?", Integer.class,
			orderId);
		return count == null ? -1 : count;
	}

	public int count(String sql, Object... args) {
		flush();
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
		return count == null ? -1 : count;
	}

	/**
	 * Pending entity changes are invisible to the JDBC read-back helpers above
	 * inside a rolled-back test transaction, so write them out first.
	 */
	private void flush() {
		entityManager.flush();
	}


	private Customer newCustomer(String slugHint, int stock, UserEntity.Role role) {
		sequence++;
		String unique = System.nanoTime() + "-" + sequence;
		String email = slugHint + "-" + unique + "@example.com";
		String slug = slugHint + "-" + unique;

		UserEntity user = new UserEntity();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setFirstName("An");
		user.setLastName("Nguyen");
		user.setRole(role);
		user.setStatus(UserEntity.Status.ACTIVE);
		user = userRepository.saveAndFlush(user);

		AddressEntity address = new AddressEntity();
		address.setUserId(user.getId());
		address.setRecipientName("An Nguyen");
		address.setPhone("0901234567");
		address.setAddressLine1("12 Le Loi");
		address.setWard("Ben Thanh");
		address.setDistrict("District 1");
		address.setCityOrProvince("Ho Chi Minh City");
		address.setPostalCode("700000");
		address.setIsDefault(Boolean.TRUE);
		address = addressRepository.saveAndFlush(address);

		CategoryEntity category = new CategoryEntity();
		category.setName("Workflow " + slug);
		category.setSlug(slug + "-category");
		category.setDescription("Workflow");
		category.setIsActive(Boolean.TRUE);
		category.setSortOrder(1);
		category = categoryRepository.saveAndFlush(category);

		ProductEntity product = new ProductEntity();
		product.setName("Workflow Shirt " + slug);
		product.setSlug(slug);
		product.setShortDescription("Workflow");
		product.setDescription("Workflow");
		product.setCategoryId(category.getId());
		product.setBasePrice(BASE_PRICE);
		product.setCurrency("VND");
		product.setStatus(ProductEntity.Status.ACTIVE);
		product.setFeatured(Boolean.FALSE);
		product = productRepository.saveAndFlush(product);

		ProductVariantEntity variant = new ProductVariantEntity();
		variant.setProductId(product.getId());
		variant.setSku("WF-" + unique);
		variant.setSize("M");
		variant.setColor("White");
		variant.setStockQuantity(stock);
		variant.setIsActive(Boolean.TRUE);
		variant = variantRepository.saveAndFlush(variant);

		ProductImageEntity image = new ProductImageEntity();
		image.setProductId(product.getId());
		image.setStorageKey("/images/" + slug + ".jpg");
		image.setAltText(product.getName());
		image.setSortOrder(0);
		image.setIsPrimary(Boolean.TRUE);
		imageRepository.saveAndFlush(image);

		return new Customer(user.getId(), email, address.getId(), variant.getId());
	}

	public record Customer(String id, String email, String addressId, String variantId) {
	}
}

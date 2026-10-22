package clothing.app.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.cart.CartEntity;
import clothing.app.domain.cart.CartItemEntity;
import clothing.app.domain.cart.CartItemRepository;
import clothing.app.domain.cart.CartRepository;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.order.dto.CreateOrderRequest;
import clothing.app.domain.order.dto.OrderResponse;
import clothing.app.domain.user.UserEntity;
import clothing.app.domain.user.UserRepository;

/**
 * Two customers check out the last unit of one variant at the same time.
 *
 * <p>Not transactional on purpose: both threads must commit independently for
 * the race to be real. Rows are cleaned up afterwards instead. The assertion is
 * the invariant that matters — one order, one decrement, no negative stock.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

	private final ExecutorService pool = Executors.newFixedThreadPool(4);

	@Autowired
	private OrderService orderService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductVariantRepository variantRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String suffix;
	private CategoryEntity category;
	private ProductEntity product;
	private ProductVariantEntity variant;
	private UserEntity first;
	private UserEntity second;
	private Attempt firstAttempt;
	private Attempt secondAttempt;

	@BeforeEach
	void seed() {
		suffix = Long.toString(System.nanoTime());

		category = new CategoryEntity();
		category.setName("Race Tops");
		category.setSlug("race-tops-" + suffix);
		category.setDescription("Concurrency fixture");
		category.setIsActive(Boolean.TRUE);
		category.setSortOrder(1);
		category = categoryRepository.save(category);

		product = new ProductEntity();
		product.setName("Race Shirt");
		product.setSlug("race-shirt-" + suffix);
		product.setShortDescription("Concurrency fixture");
		product.setDescription("Concurrency fixture");
		product.setCategoryId(category.getId());
		product.setBasePrice(100000L);
		product.setCurrency("VND");
		product.setStatus(ProductEntity.Status.ACTIVE);
		product.setFeatured(Boolean.FALSE);
		product = productRepository.save(product);

		variant = new ProductVariantEntity();
		variant.setProductId(product.getId());
		variant.setSku("RACE-" + suffix);
		variant.setSize("M");
		variant.setColor("White");
		variant.setStockQuantity(1);
		variant.setIsActive(Boolean.TRUE);
		variant = variantRepository.save(variant);

		first = userRepository.save(user("race-a-" + suffix + "@example.com"));
		second = userRepository.save(user("race-b-" + suffix + "@example.com"));
		firstAttempt = attemptFor(first, "racekeya" + suffix);
		secondAttempt = attemptFor(second, "racekeyb" + suffix);
	}

	@AfterEach
	void cleanUp() throws Exception {
		pool.shutdownNow();
		pool.awaitTermination(5, TimeUnit.SECONDS);

		for (UserEntity user : List.of(first, second)) {
			String userId = user.getId();
			jdbcTemplate.update("delete from order_items where order_id in "
				+ "(select id from orders where user_id = ?)", userId);
			jdbcTemplate.update("delete from order_status_history where order_id in "
				+ "(select id from orders where user_id = ?)", userId);
			jdbcTemplate.update("delete from payments where order_id in "
				+ "(select id from orders where user_id = ?)", userId);
			jdbcTemplate.update("delete from idempotency_records where user_id = ?", userId);
			jdbcTemplate.update("delete from orders where user_id = ?", userId);
			jdbcTemplate.update("delete from cart_items where cart_id in "
				+ "(select id from carts where user_id = ?)", userId);
			jdbcTemplate.update("delete from carts where user_id = ?", userId);
			jdbcTemplate.update("delete from addresses where user_id = ?", userId);
			jdbcTemplate.update("delete from users where id = ?", userId);
		}
		variantRepository.delete(variant);
		productRepository.delete(product);
		categoryRepository.delete(category);
	}

	@Test
	void onlyOneCheckoutTakesTheLastUnit() throws Exception {
		List<Object> results = runBoth();

		List<OrderResponse> orders = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();
		for (Object result : results) {
			if (result instanceof OrderResponse order) {
				orders.add(order);
			}
			else {
				failures.add((Throwable) result);
			}
		}

		assertThat(orders).hasSize(1);
		assertThat(failures).hasSize(1);
		assertThat(failures.get(0)).isInstanceOf(ApiException.OutOfStock.class);
		assertThat(orders.get(0).items()).hasSize(1);
		assertThat(stockOf(variant.getId())).isZero();
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from orders where user_id in (?, ?)", Integer.class,
			first.getId(), second.getId())).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from idempotency_records where user_id in (?, ?)", Integer.class,
			first.getId(), second.getId())).isEqualTo(1);
	}

	/* --- helpers ----------------------------------------------------------- */

	private record Attempt(String key, CreateOrderRequest request) {
	}

	private List<Object> runBoth() throws Exception {
		List<Callable<Object>> tasks = List.of(
			() -> perform(first.getId(), firstAttempt),
			() -> perform(second.getId(), secondAttempt));
		List<Object> results = new ArrayList<>();
		for (Future<Object> future : pool.invokeAll(tasks)) {
			results.add(future.get(30, TimeUnit.SECONDS));
		}
		return results;
	}

	/** Runs one order and returns either the response or the failure. */
	private Object perform(String userId, Attempt attempt) {
		try {
			return orderService.create(userId, attempt.key(), attempt.request());
		}
		catch (RuntimeException ex) {
			return ex;
		}
	}

	private Attempt attemptFor(UserEntity user, String key) {
		return new Attempt(key, new CreateOrderRequest(cartWithOneLine(user), "STANDARD", null,
			null));
	}

	private int stockOf(String variantId) {
		Integer stock = jdbcTemplate.queryForObject(
			"select stock_quantity from product_variants where id = ?", Integer.class,
			variantId);
		return stock == null ? -1 : stock;
	}

	private UserEntity user(String email) {
		UserEntity user = new UserEntity();
		user.setEmail(email);
		user.setPasswordHash("$2a$10$notarealhashnotarealhashnotarealhashnotarealhashnotarealha");
		user.setFirstName("Race");
		user.setLastName("Customer");
		user.setRole(UserEntity.Role.CUSTOMER);
		user.setStatus(UserEntity.Status.ACTIVE);
		return user;
	}

	/** Creates the cart + line + address and returns the address id. */
	private String cartWithOneLine(UserEntity user) {
		CartEntity cart = new CartEntity();
		cart.setUserId(user.getId());
		cart = cartRepository.save(cart);

		CartItemEntity item = new CartItemEntity();
		item.setCartId(cart.getId());
		item.setVariantId(variant.getId());
		item.setQuantity(1);
		cartItemRepository.save(item);

		AddressEntity address = new AddressEntity();
		address.setUserId(user.getId());
		address.setRecipientName("Race Customer");
		address.setPhone("0900000000");
		address.setAddressLine1("1 Race Street");
		address.setCityOrProvince("Ho Chi Minh City");
		address.setIsDefault(Boolean.TRUE);
		return addressRepository.save(address).getId();
	}
}

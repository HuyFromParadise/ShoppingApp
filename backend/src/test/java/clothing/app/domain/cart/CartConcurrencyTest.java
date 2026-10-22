package clothing.app.domain.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.springframework.test.context.ActiveProfiles;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.domain.cart.dto.AddCartItemRequest;
import clothing.app.domain.cart.dto.CartResponse;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.user.UserEntity;
import clothing.app.domain.user.UserRepository;

/**
 * Parallel requests on one cart, against the real MySQL schema.
 *
 * <p>Not transactional on purpose: the two threads must see each other's
 * commits, which a test-managed transaction would hide. The rows are cleaned up
 * afterwards instead.
 */
@SpringBootTest
@ActiveProfiles("test")
class CartConcurrencyTest {

	private final ExecutorService pool = Executors.newFixedThreadPool(8);

	@Autowired
	private CartService cartService;

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
	private ProductImageRepository imageRepository;

	@Autowired
	private UserRepository userRepository;

	private String suffix;
	private UserEntity user;
	private ProductEntity product;
	private ProductVariantEntity variant;

	@BeforeEach
	void seed() {
		suffix = Long.toString(System.nanoTime());

		CategoryEntity category = new CategoryEntity();
		category.setName("Concurrent Tops");
		category.setSlug("concurrent-tops-" + suffix);
		category.setDescription("Concurrency fixture");
		category.setIsActive(Boolean.TRUE);
		category.setSortOrder(1);
		category = categoryRepository.save(category);

		product = new ProductEntity();
		product.setName("Concurrent Shirt");
		product.setSlug("concurrent-shirt-" + suffix);
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
		variant.setSku("CONC-" + suffix);
		variant.setSize("M");
		variant.setColor("White");
		variant.setStockQuantity(20);
		variant.setIsActive(Boolean.TRUE);
		variant = variantRepository.save(variant);

		user = new UserEntity();
		user.setEmail("cart-concurrent-" + suffix + "@example.com");
		user.setPasswordHash("$2a$10$notarealhashnotarealhashnotarealhashnotarealhashnotarealha");
		user.setFirstName("Con");
		user.setLastName("Current");
		user.setRole(UserEntity.Role.CUSTOMER);
		user.setStatus(UserEntity.Status.ACTIVE);
		user = userRepository.save(user);
	}

	@AfterEach
	void cleanUp() throws Exception {
		pool.shutdownNow();
		pool.awaitTermination(5, TimeUnit.SECONDS);

		cartRepository.findByUserId(user.getId())
			.ifPresent(cart -> {
				cartItemRepository.deleteByCartId(cart.getId());
				cartRepository.delete(cart);
			});
		userRepository.delete(user);
		variantRepository.delete(variant);
		productRepository.delete(product);
		categoryRepository.findById(product.getCategoryId())
			.ifPresent(categoryRepository::delete);
	}

	@Test
	void parallelFirstLoadsOpenOneCart() throws Exception {
		List<String> ids = runAll(8, () -> cartService.get(user.getId()).id());

		assertThat(Set.copyOf(ids)).hasSize(1);
		assertThat(ids).allMatch(id -> id != null);
	}

	@Test
	void parallelAddsOfTheSameVariantKeepOneLine() throws Exception {
		List<String> cartIds = runAll(8, () -> {
			CartResponse cart = cartService.addItem(user.getId(),
				new AddCartItemRequest(variant.getId(), 1));
			return cart.id();
		});

		assertThat(Set.copyOf(cartIds)).hasSize(1);
		CartResponse cart = cartService.get(user.getId());
		assertThat(cart.items()).hasSize(1);
		assertThat(cart.items().get(0).quantity()).isEqualTo(8);
		assertThat(cart.itemCount()).isEqualTo(8);
		assertThat(cart.subtotalMinor()).isEqualTo(8 * 100000L);
	}

	private <T> List<T> runAll(int threads, Callable<T> task) throws Exception {
		List<Callable<T>> tasks = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			tasks.add(task);
		}
		List<T> results = new ArrayList<>();
		for (Future<T> future : pool.invokeAll(tasks)) {
			results.add(future.get(30, TimeUnit.SECONDS));
		}
		return results;
	}
}

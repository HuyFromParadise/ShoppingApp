package clothing.app.domain.cart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.cart.dto.AddCartItemRequest;
import clothing.app.domain.cart.dto.CartItemResponse;
import clothing.app.domain.cart.dto.CartResponse;
import clothing.app.domain.cart.dto.UpdateCartItemRequest;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.user.UserRepository;

/**
 * The signed-in customer's cart (DESIGN_REFERENCE §5.3, §5.4).
 *
 * <p>The cart is owned by the token holder and is created on first use. Prices
 * are always recomputed from the catalog; the only stored data is the variant
 * and the quantity. Stock is validated here as a courtesy, but the authoritative
 * check happens at checkout, which deducts stock conditionally.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CartService {

	/** Per-line cap from the validation table (DESIGN_REFERENCE §5.4). */
	private static final int MAX_LINE_QUANTITY = AddCartItemRequest.MAX_LINE_QUANTITY;

	private static final String DEFAULT_CURRENCY = "VND";

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductVariantRepository variantRepository;
	private final ProductRepository productRepository;
	private final ProductImageRepository imageRepository;
	private final UserRepository userRepository;

	public CartService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		ProductVariantRepository variantRepository,
		ProductRepository productRepository,
		ProductImageRepository imageRepository,
		UserRepository userRepository) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.variantRepository = variantRepository;
		this.productRepository = productRepository;
		this.imageRepository = imageRepository;
		this.userRepository = userRepository;
	}

	public CartResponse get(String userId) {
		return toResponse(cartOf(userId, false));
	}

	public CartResponse addItem(String userId, AddCartItemRequest request) {
		CartEntity cart = cartOf(userId, true);
		ProductVariantEntity variant = requirePurchasableVariant(request.variantId());

		CartItemEntity item = cartItemRepository
			.findByCartIdAndVariantId(cart.getId(), variant.getId())
			.orElse(null);
		int target = request.quantity() + (item == null ? 0 : item.getQuantity());
		requireWithinLimits(target, variant);

		if (item == null) {
			item = new CartItemEntity();
			item.setCartId(cart.getId());
			item.setVariantId(variant.getId());
		}
		item.setQuantity(target);
		cartItemRepository.save(item);
		return toResponse(cart);
	}

	public CartResponse updateItem(String userId, String itemId, UpdateCartItemRequest request) {
		CartEntity cart = cartOf(userId, true);
		CartItemEntity item = requireOwnedItem(cart, itemId);
		ProductVariantEntity variant = requireVariant(item.getVariantId());
		requireWithinLimits(request.quantity(), variant);

		item.setQuantity(request.quantity());
		cartItemRepository.save(item);
		return toResponse(cart);
	}

	public void removeItem(String userId, String itemId) {
		CartEntity cart = cartOf(userId, true);
		cartItemRepository.delete(requireOwnedItem(cart, itemId));
	}

	public void clear(String userId) {
		cartItemRepository.deleteByCartId(cartOf(userId, true).getId());
	}

	/* --- ownership and catalog rules --------------------------------------- */

	/**
	 * The cart of this user, created on first use.
	 *
	 * <p>Creating the cart and changing it both take the user row lock: that is
	 * the per-customer mutex, so two parallel requests cannot insert two carts
	 * (unique key on {@code user_id}) or two copies of the same line, and cannot
	 * overwrite each other's quantity. {@code READ_COMMITTED} then makes the
	 * re-read see the row the winner committed instead of a stale snapshot. A
	 * plain read skips the lock entirely.
	 */
	private CartEntity cartOf(String userId, boolean forUpdate) {
		requireUser(userId);
		if (!forUpdate) {
			Optional<CartEntity> existing = cartRepository.findByUserId(userId);
			if (existing.isPresent()) {
				return existing.get();
			}
		}
		userRepository.findByIdForUpdate(userId)
			.orElseThrow(() -> new ApiException.Unauthenticated("Authentication required"));
		return cartRepository.findByUserId(userId).orElseGet(() -> {
			CartEntity cart = new CartEntity();
			cart.setUserId(userId);
			return cartRepository.save(cart);
		});
	}

	/** A line of this user's cart, or NOT_FOUND - never another user's line. */
	private CartItemEntity requireOwnedItem(CartEntity cart, String itemId) {
		return cartItemRepository.findByIdAndCartId(itemId, cart.getId())
			.orElseThrow(() -> new ApiException.NotFound("Cart item not found"));
	}

	private ProductVariantEntity requirePurchasableVariant(String variantId) {
		ProductVariantEntity variant = requireVariant(variantId);
		ProductEntity product = requireProduct(variant.getProductId());
		if (!isPurchasable(product, variant)) {
			throw new ApiException.NotFound("Variant not found");
		}
		return variant;
	}

	private ProductVariantEntity requireVariant(String variantId) {
		return variantRepository.findById(variantId)
			.orElseThrow(() -> new ApiException.NotFound("Variant not found"));
	}

	private ProductEntity requireProduct(String productId) {
		return productRepository.findById(productId)
			.orElseThrow(() -> new ApiException.NotFound("Variant not found"));
	}

	/** The per-line cap is a request problem; the stock is a moving condition. */
	private void requireWithinLimits(int quantity, ProductVariantEntity variant) {
		if (quantity < 1 || quantity > MAX_LINE_QUANTITY) {
			throw new ApiException.Validation("Quantity must be between 1 and "
				+ MAX_LINE_QUANTITY, Map.of("quantity",
					"Must be between 1 and " + MAX_LINE_QUANTITY + " per line"));
		}
		int stock = stockOf(variant);
		if (quantity > stock) {
			throw new ApiException.OutOfStock(stock == 0
				? "This item is out of stock"
				: "Only " + stock + " left in stock");
		}
	}

	private void requireUser(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
	}

	private static boolean isPurchasable(ProductEntity product, ProductVariantEntity variant) {
		return product.getStatus() == ProductEntity.Status.ACTIVE
			&& Boolean.TRUE.equals(variant.getIsActive());
	}

	private static int stockOf(ProductVariantEntity variant) {
		return variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
	}

	private static long unitPriceOf(ProductEntity product, ProductVariantEntity variant) {
		return variant.getPriceOverride() == null
			? product.getBasePrice()
			: variant.getPriceOverride();
	}

	/* --- response ---------------------------------------------------------- */

	private CartResponse toResponse(CartEntity cart) {
		List<CartItemEntity> lines = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
		if (lines.isEmpty()) {
			return new CartResponse(cart.getId(), List.of(), 0, 0L, DEFAULT_CURRENCY);
		}

		Map<String, ProductVariantEntity> variants = variantRepository
			.findAllById(lines.stream().map(CartItemEntity::getVariantId).toList()).stream()
			.collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
		Map<String, ProductEntity> products = productRepository
			.findAllById(variants.values().stream()
				.map(ProductVariantEntity::getProductId).distinct().toList())
			.stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
		Map<String, String> imageUrls = firstImageUrls(products.keySet());

		List<CartItemResponse> items = new ArrayList<>();
		int itemCount = 0;
		long subtotal = 0L;
		String currency = DEFAULT_CURRENCY;

		for (CartItemEntity line : lines) {
			ProductVariantEntity variant = variants.get(line.getVariantId());
			ProductEntity product = variant == null ? null : products.get(variant.getProductId());
			if (product == null) {
				// The foreign keys make this impossible; fail loudly rather than
				// return a cart whose subtotal silently omits a line.
				throw new IllegalStateException(
					"Cart item " + line.getId() + " points at a missing product");
			}
			long unitPrice = unitPriceOf(product, variant);
			long lineTotal = unitPrice * line.getQuantity();
			int stock = stockOf(variant);

			items.add(new CartItemResponse(
				line.getId(),
				variant.getId(),
				product.getId(),
				product.getName(),
				product.getSlug(),
				imageUrls.get(product.getId()),
				variant.getSku(),
				variant.getSize(),
				variant.getColor(),
				unitPrice,
				line.getQuantity(),
				lineTotal,
				stock,
				isPurchasable(product, variant) && stock >= line.getQuantity()));
			itemCount += line.getQuantity();
			subtotal += lineTotal;
			if (items.size() == 1) {
				currency = product.getCurrency();
			}
		}

		return new CartResponse(cart.getId(), items, itemCount, subtotal, currency);
	}

	private Map<String, String> firstImageUrls(Collection<String> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		Map<String, String> urls = new HashMap<>();
		for (ProductImageEntity image : imageRepository
			.findByProductIdInOrderByIsPrimaryDescSortOrderAsc(productIds)) {
			urls.putIfAbsent(image.getProductId(), image.getStorageKey());
		}
		return urls;
	}
}

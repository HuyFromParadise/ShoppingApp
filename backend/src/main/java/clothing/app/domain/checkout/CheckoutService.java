package clothing.app.domain.checkout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.address.AddressEntity;
import clothing.app.domain.address.AddressRepository;
import clothing.app.domain.cart.CartEntity;
import clothing.app.domain.cart.CartItemEntity;
import clothing.app.domain.cart.CartItemRepository;
import clothing.app.domain.cart.CartRepository;
import clothing.app.domain.cart.dto.AddCartItemRequest;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.checkout.dto.QuoteRequest;
import clothing.app.domain.checkout.dto.QuoteResponse;

/**
 * Authoritative checkout pricing (DESIGN_REFERENCE §5.3, §8).
 *
 * <p>Every number is recomputed from the catalog; the only client inputs are the
 * address, the shipping code, an optional promotion code and (for order
 * creation) a note. Product/variant state and stock are re-validated here
 * because the cart is only a preview.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CheckoutService {

	private static final int MAX_LINE_QUANTITY = AddCartItemRequest.MAX_LINE_QUANTITY;
	private static final String DEFAULT_CURRENCY = "VND";

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final AddressRepository addressRepository;
	private final ProductVariantRepository variantRepository;
	private final ProductRepository productRepository;
	private final ProductImageRepository imageRepository;
	private final ShippingStrategy shippingStrategy;

	public CheckoutService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		AddressRepository addressRepository,
		ProductVariantRepository variantRepository,
		ProductRepository productRepository,
		ProductImageRepository imageRepository,
		ShippingStrategy shippingStrategy) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.addressRepository = addressRepository;
		this.variantRepository = variantRepository;
		this.productRepository = productRepository;
		this.imageRepository = imageRepository;
		this.shippingStrategy = shippingStrategy;
	}

	public QuoteResponse quote(String userId, QuoteRequest request) {
		return toQuote(price(userId, request.addressId(), request.shippingMethod(),
			request.promotionCode(), false));
	}

	/**
	 * Prices the customer's cart for an owned address.
	 *
	 * @param lockVariants {@code true} for order creation: the variants stay
	 *     locked until the transaction ends, so the stock seen here is the stock
	 *     decremented next (DESIGN_REFERENCE §8, step 2).
	 */
	public PricedCheckout price(String userId, String addressId, String shippingMethod,
			String promotionCode, boolean lockVariants) {
		requireUser(userId);
		requireNoPromotion(promotionCode);

		AddressEntity address = addressRepository.findByIdAndUserId(addressId, userId)
			.orElseThrow(() -> new ApiException.NotFound("Address not found"));

		CartEntity cart = cartRepository.findByUserId(userId)
			.orElseThrow(CheckoutService::emptyCart);
		List<CartItemEntity> cartItems = cartItemRepository
			.findByCartIdOrderByCreatedAtAsc(cart.getId());
		if (cartItems.isEmpty()) {
			throw emptyCart();
		}

		List<String> variantIds = cartItems.stream()
			.map(CartItemEntity::getVariantId).distinct().sorted().toList();
		List<ProductVariantEntity> variants = lockVariants
			? variantRepository.findAllForUpdateByIdIn(variantIds)
			: variantRepository.findAllById(variantIds);
		Map<String, ProductVariantEntity> variantsById = variants.stream()
			.collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
		Map<String, ProductEntity> productsById = productRepository
			.findAllById(variants.stream().map(ProductVariantEntity::getProductId)
				.distinct().toList())
			.stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
		Map<String, String> imageUrls = firstImageUrls(productsById.keySet());

		List<PricedCheckout.Line> lines = new ArrayList<>();
		int itemCount = 0;
		long subtotal = 0L;
		String currency = DEFAULT_CURRENCY;

		for (CartItemEntity item : cartItems) {
			ProductVariantEntity variant = variantsById.get(item.getVariantId());
			ProductEntity product = variant == null ? null : productsById.get(variant.getProductId());
			if (product == null) {
				// The foreign keys make this impossible; fail loudly rather than
				// price a cart that silently drops a line.
				throw new IllegalStateException(
					"Cart item " + item.getId() + " points at a missing product");
			}
			requirePurchasable(product, variant);
			requireStock(product, variant, item.getQuantity());

			long unitPrice = unitPriceOf(product, variant);
			long lineTotal = unitPrice * item.getQuantity();
			lines.add(new PricedCheckout.Line(product, variant, imageUrls.get(product.getId()),
				item.getQuantity(), unitPrice, lineTotal));

			itemCount += item.getQuantity();
			subtotal += lineTotal;
			if (lines.size() == 1) {
				currency = product.getCurrency();
			}
		}

		ShippingStrategy.Method shipping = shippingStrategy.resolve(shippingMethod);
		long discount = 0L;
		long grandTotal = subtotal - discount + shipping.feeMinor();
		return new PricedCheckout(cart.getId(), address, List.copyOf(lines), itemCount,
			subtotal, discount, shipping.feeMinor(), grandTotal, currency, shipping);
	}

	private static QuoteResponse toQuote(PricedCheckout checkout) {
		return new QuoteResponse(
			checkout.currency(),
			checkout.itemCount(),
			checkout.subtotalMinor(),
			checkout.discountTotalMinor(),
			checkout.shippingTotalMinor(),
			checkout.grandTotalMinor(),
			checkout.shippingMethod().code(),
			checkout.shippingMethod().name());
	}

	/* --- rules -------------------------------------------------------------- */

	private static ApiException emptyCart() {
		return new ApiException.Validation("Your cart is empty");
	}

	private static void requireNoPromotion(String promotionCode) {
		// Promotions are P1 (DESIGN_REFERENCE §4). Reject rather than ignore, so
		// the server never quotes a total it cannot honour.
		if (promotionCode != null && !promotionCode.isBlank()) {
			throw new ApiException.Validation("Promotion codes are not available yet",
				Map.of("promotionCode", "Not available yet"));
		}
	}

	private static void requirePurchasable(ProductEntity product, ProductVariantEntity variant) {
		if (product.getStatus() != ProductEntity.Status.ACTIVE
				|| !Boolean.TRUE.equals(variant.getIsActive())) {
			throw new ApiException.Conflict(product.getName() + " is no longer available");
		}
	}

	private static void requireStock(ProductEntity product, ProductVariantEntity variant,
			int quantity) {
		if (quantity < 1 || quantity > MAX_LINE_QUANTITY) {
			throw new ApiException.Validation("Quantity must be between 1 and "
				+ MAX_LINE_QUANTITY);
		}
		int stock = stockOf(variant);
		if (quantity > stock) {
			throw new ApiException.OutOfStock(stock == 0
				? product.getName() + " is out of stock"
				: "Only " + stock + " left in stock");
		}
	}

	private static void requireUser(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
	}

	private static int stockOf(ProductVariantEntity variant) {
		return variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
	}

	private static long unitPriceOf(ProductEntity product, ProductVariantEntity variant) {
		return variant.getPriceOverride() == null
			? product.getBasePrice()
			: variant.getPriceOverride();
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

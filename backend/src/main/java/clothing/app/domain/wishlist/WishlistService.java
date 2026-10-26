package clothing.app.domain.wishlist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

/**
 * The customer's wishlist (PROJECT_SPEC §7, P1).
 *
 * <p><b>Products, stored as variants.</b> The customer saves a <i>product</i>
 * (this is what the wishlist page shows and what {@code DELETE
 * /wishlist/items/{productId}} removes), but {@code wishlist_items} keys on
 * {@code variant_id} and enforces uniqueness per variant. A saved product is
 * therefore represented by one of its active variants — a canonical one, chosen
 * deterministically (active first, then lowest effective price, then id) so the
 * same product always maps to the same row and a re-save is idempotent.
 *
 * <p>The alternative — storing the variant the customer was looking at — would
 * let one product appear several times and would break the product-keyed delete,
 * so it is deliberately not done. The trade-off is that a product with no active
 * variant cannot be saved; that is also a product nobody can buy.
 */
@Service
public class WishlistService {

	private final WishlistRepository wishlistRepository;
	private final WishlistItemRepository itemRepository;
	private final ProductRepository productRepository;
	private final ProductVariantRepository variantRepository;
	private final ProductImageRepository imageRepository;

	public WishlistService(WishlistRepository wishlistRepository,
			WishlistItemRepository itemRepository, ProductRepository productRepository,
			ProductVariantRepository variantRepository,
			ProductImageRepository imageRepository) {
		this.wishlistRepository = wishlistRepository;
		this.itemRepository = itemRepository;
		this.productRepository = productRepository;
		this.variantRepository = variantRepository;
		this.imageRepository = imageRepository;
	}

	/** The saved products, newest first, with their current availability. */
	@Transactional
	public List<WishlistEntry> list(String userId) {
		requireUser(userId);
		WishlistEntity wishlist = wishlistRepository.findByUserId(userId).orElse(null);
		if (wishlist == null) {
			return List.of();
		}
		List<WishlistItemEntity> items = itemRepository
			.findByWishlistIdOrderByCreatedAtDesc(wishlist.getId());
		if (items.isEmpty()) {
			return List.of();
		}

		Map<String, ProductVariantEntity> variants = variantRepository
			.findAllById(items.stream().map(WishlistItemEntity::getVariantId).toList())
			.stream()
			.collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
		List<String> productIds = variants.values().stream()
			.map(ProductVariantEntity::getProductId).distinct().toList();
		Map<String, ProductEntity> products = productRepository.findAllById(productIds)
			.stream()
			.collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
		Map<String, String> imageUrls = firstImageUrl(productIds);

		List<WishlistEntry> entries = new ArrayList<>();
		for (WishlistItemEntity item : items) {
			ProductVariantEntity variant = variants.get(item.getVariantId());
			ProductEntity product = variant == null ? null : products.get(variant.getProductId());
			if (product == null) {
				// The row points at something that no longer exists; skip it rather
				// than fail the whole page.
				continue;
			}
			List<ProductVariantEntity> active = variantRepository
				.findByProductIdAndIsActiveTrueOrderBySizeAscColorAsc(product.getId());
			long price = active.stream().mapToLong(candidate -> priceOf(product, candidate))
				.min()
				.orElse(priceOf(product, variant));
			boolean inStock = active.stream()
				.anyMatch(candidate -> stockOf(candidate) > 0);
			entries.add(new WishlistEntry(
				product.getId(),
				product.getName(),
				product.getSlug(),
				product.getCurrency(),
				price,
				product.getBasePrice() > price ? product.getBasePrice() : null,
				inStock,
				product.getStatus() == ProductEntity.Status.ACTIVE,
				imageUrls.get(product.getId()),
				item.getCreatedAt()));
		}
		return entries;
	}

	/**
	 * Saves a product. Saving twice is not an error and does not duplicate the
	 * entry, so a double tap or a retried request is harmless.
	 */
	@Transactional
	public List<WishlistEntry> save(String userId, String productId) {
		requireUser(userId);
		ProductEntity product = productRepository.findById(productId)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));
		if (product.getStatus() == ProductEntity.Status.ARCHIVED) {
			throw new ApiException.Conflict("This product is no longer available");
		}
		WishlistEntity wishlist = wishlistRepository.findByUserId(userId)
			.orElseGet(() -> createWishlist(userId));

		// Already saved under any of this product's variants: nothing to do.
		List<String> variantIds = variantRepository
			.findByProductIdAndIsActiveTrueOrderBySizeAscColorAsc(product.getId())
			.stream()
			.map(ProductVariantEntity::getId)
			.toList();
		if (!variantIds.isEmpty()
				&& !itemRepository.findByWishlistIdAndVariantIdIn(wishlist.getId(), variantIds)
					.isEmpty()) {
			return list(userId);
		}

		ProductVariantEntity canonical = canonicalVariant(product).orElseThrow(
			() -> new ApiException.Conflict("This product has no variants available"));
		WishlistItemEntity item = new WishlistItemEntity();
		item.setWishlistId(wishlist.getId());
		item.setVariantId(canonical.getId());
		itemRepository.save(item);
		return list(userId);
	}

	/** Removes a product from the wishlist; a product that is not saved is a no-op. */
	@Transactional
	public List<WishlistEntry> remove(String userId, String productId) {
		requireUser(userId);
		WishlistEntity wishlist = wishlistRepository.findByUserId(userId).orElse(null);
		if (wishlist == null) {
			return List.of();
		}
		List<String> variantIds = variantRepository
			.findByProductIdOrderBySizeAscColorAsc(productId)
			.stream()
			.map(ProductVariantEntity::getId)
			.toList();
		for (String variantId : variantIds) {
			itemRepository.deleteByWishlistIdAndVariantId(wishlist.getId(), variantId);
		}
		return list(userId);
	}

	/** Whether one product is saved, for the product page's toggle state. */
	@Transactional
	public boolean contains(String userId, String productId) {
		if (userId == null) {
			return false;
		}
		WishlistEntity wishlist = wishlistRepository.findByUserId(userId).orElse(null);
		if (wishlist == null) {
			return false;
		}
		for (ProductVariantEntity variant : variantRepository
			.findByProductIdOrderBySizeAscColorAsc(productId)) {
			if (itemRepository.existsByWishlistIdAndVariantId(wishlist.getId(), variant.getId())) {
				return true;
			}
		}
		return false;
	}

	/* --- helpers ------------------------------------------------------------ */

	private WishlistEntity createWishlist(String userId) {
		WishlistEntity wishlist = new WishlistEntity();
		wishlist.setUserId(userId);
		return wishlistRepository.save(wishlist);
	}

	/**
	 * The variant that represents a product in the wishlist: active variants
	 * only, cheapest first, then by id so the choice is stable across requests.
	 */
	private java.util.Optional<ProductVariantEntity> canonicalVariant(ProductEntity product) {
		return variantRepository.findByProductIdAndIsActiveTrueOrderBySizeAscColorAsc(product.getId())
			.stream()
			.min(Comparator.comparingLong((ProductVariantEntity variant) -> priceOf(product, variant))
				.thenComparing(ProductVariantEntity::getId));
	}

	private Map<String, String> firstImageUrl(Collection<String> productIds) {
		Map<String, String> urls = new LinkedHashMap<>();
		if (productIds.isEmpty()) {
			return urls;
		}
		for (ProductImageEntity image : imageRepository
			.findByProductIdInOrderByIsPrimaryDescSortOrderAsc(productIds)) {
			urls.putIfAbsent(image.getProductId(), image.getStorageKey());
		}
		return urls;
	}

	private static long priceOf(ProductEntity product, ProductVariantEntity variant) {
		return variant.getPriceOverride() == null ? product.getBasePrice()
			: variant.getPriceOverride();
	}

	private static int stockOf(ProductVariantEntity variant) {
		return variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
	}

	private static void requireUser(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
	}

	/** One saved product, priced and availability-checked as of now. */
	public record WishlistEntry(
			String productId,
			String productName,
			String productSlug,
			String currency,
			long priceMinor,
			Long compareAtPriceMinor,
			boolean inStock,
			boolean purchasable,
			String imageUrl,
			java.time.Instant savedAt) {
	}
}

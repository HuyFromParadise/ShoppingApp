package clothing.app.domain.wishlist;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, String> {

	List<WishlistItemEntity> findByWishlistIdOrderByCreatedAtDesc(String wishlistId);

	Optional<WishlistItemEntity> findByWishlistIdAndVariantId(String wishlistId, String variantId);

	/** Every saved row for one wishlist whose variant is in the given set. */
	List<WishlistItemEntity> findByWishlistIdAndVariantIdIn(String wishlistId,
		Collection<String> variantIds);

	boolean existsByWishlistIdAndVariantId(String wishlistId, String variantId);

	void deleteByWishlistIdAndVariantId(String wishlistId, String variantId);
}

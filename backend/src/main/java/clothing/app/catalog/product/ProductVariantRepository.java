package clothing.app.catalog.product;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import clothing.app.domain.catalog.product.ProductVariantEntity;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, String> {

	List<ProductVariantEntity> findByProductIdInAndIsActiveTrueOrderBySizeAscColorAsc(
		Collection<String> productIds);

	List<ProductVariantEntity> findByProductIdAndIsActiveTrueOrderBySizeAscColorAsc(
		String productId);

	/**
	 * Locks the variants of a checkout until the end of the transaction, ordered
	 * by id so concurrent checkouts take the same locks in the same order
	 * (DESIGN_REFERENCE §8, step 2).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from ProductVariantEntity v where v.id in :ids order by v.id")
	List<ProductVariantEntity> findAllForUpdateByIdIn(@Param("ids") Collection<String> ids);

	/**
	 * Atomic stock decrement (ADR-005). The {@code stock_quantity >= :quantity}
	 * predicate makes the check and the write one statement, so two checkouts
	 * cannot both take the last unit. Returns the number of rows updated.
	 */
	@Modifying(flushAutomatically = true)
	@Query("update ProductVariantEntity v set v.stockQuantity = v.stockQuantity - :quantity, "
		+ "v.updatedAt = :updatedAt where v.id = :id and v.stockQuantity >= :quantity")
	int decrementStock(@Param("id") String id, @Param("quantity") int quantity,
		@Param("updatedAt") Instant updatedAt);
}

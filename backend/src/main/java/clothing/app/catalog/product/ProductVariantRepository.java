package clothing.app.catalog.product;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import clothing.app.domain.catalog.product.ProductVariantEntity;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, String> {

	List<ProductVariantEntity> findByProductIdInAndIsActiveTrueOrderBySizeAscColorAsc(
		Collection<String> productIds);

	List<ProductVariantEntity> findByProductIdAndIsActiveTrueOrderBySizeAscColorAsc(
		String productId);
}

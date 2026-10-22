package clothing.app.catalog.product;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import clothing.app.domain.catalog.product.ProductImageEntity;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, String> {

	List<ProductImageEntity> findByProductIdInOrderByIsPrimaryDescSortOrderAsc(
		Collection<String> productIds);

	List<ProductImageEntity> findByProductIdOrderByIsPrimaryDescSortOrderAsc(String productId);
}

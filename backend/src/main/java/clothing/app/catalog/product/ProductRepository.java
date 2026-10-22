package clothing.app.catalog.product;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import clothing.app.domain.catalog.product.ProductEntity;

public interface ProductRepository
	extends JpaRepository<ProductEntity, String>, JpaSpecificationExecutor<ProductEntity> {

	Optional<ProductEntity> findBySlugAndStatus(String slug, ProductEntity.Status status);
}

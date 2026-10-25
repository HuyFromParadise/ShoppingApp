package clothing.app.catalog.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import clothing.app.domain.catalog.category.CategoryEntity;

public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {

	List<CategoryEntity> findByIsActiveTrueOrderBySortOrderAscNameAsc();

	Optional<CategoryEntity> findBySlugAndIsActiveTrue(String slug);

	Optional<CategoryEntity> findBySlug(String slug);

	Optional<CategoryEntity> findByIdAndIsActiveTrue(String id);
}

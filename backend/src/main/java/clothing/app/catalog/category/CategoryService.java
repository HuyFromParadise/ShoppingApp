package clothing.app.catalog.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.category.dto.CategoryResponse;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listActive() {
		return categoryRepository.findByIsActiveTrueOrderBySortOrderAscNameAsc()
			.stream()
			.map(CategoryResponse::from)
			.toList();
	}
}

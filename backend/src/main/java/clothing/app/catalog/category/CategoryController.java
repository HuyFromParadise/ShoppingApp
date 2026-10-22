package clothing.app.catalog.category;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.catalog.category.dto.CategoryResponse;
import clothing.app.catalog.product.ProductQuery;
import clothing.app.catalog.product.ProductService;
import clothing.app.catalog.product.dto.ProductSummaryResponse;
import clothing.app.common.response.ApiResponse;
import clothing.app.common.response.PageResponse;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

	private final CategoryService categoryService;
	private final ProductService productService;

	public CategoryController(CategoryService categoryService, ProductService productService) {
		this.categoryService = categoryService;
		this.productService = productService;
	}

	@GetMapping
	public ApiResponse<List<CategoryResponse>> list() {
		return ApiResponse.success(categoryService.listActive());
	}

	@GetMapping("/{slug}/products")
	public PageResponse<ProductSummaryResponse> products(
		@PathVariable String slug,
		@Valid @ModelAttribute ProductQuery query) {
		return productService.listForCategory(slug, query);
	}
}

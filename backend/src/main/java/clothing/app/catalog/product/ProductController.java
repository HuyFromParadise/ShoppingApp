package clothing.app.catalog.product;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.catalog.product.dto.ProductDetailResponse;
import clothing.app.catalog.product.dto.ProductSummaryResponse;
import clothing.app.common.response.ApiResponse;
import clothing.app.common.response.PageResponse;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	public PageResponse<ProductSummaryResponse> list(@Valid @ModelAttribute ProductQuery query) {
		return productService.list(query);
	}

	@GetMapping("/{slug}")
	public ApiResponse<ProductDetailResponse> detail(@PathVariable String slug) {
		return ApiResponse.success(productService.getBySlug(slug));
	}
}

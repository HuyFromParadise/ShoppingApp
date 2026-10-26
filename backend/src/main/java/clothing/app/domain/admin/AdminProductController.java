package clothing.app.domain.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.response.PageResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.admin.dto.AdminImageResponse;
import clothing.app.domain.admin.dto.AdminProductDetailResponse;
import clothing.app.domain.admin.dto.AdminProductRequests.CreateProductRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.SetStockRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.UpdateProductRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.UpdateVariantRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.VariantRequest;
import clothing.app.domain.admin.dto.AdminProductSummaryResponse;
import clothing.app.domain.admin.dto.AdminVariantResponse;

/**
 * Admin catalog API (DESIGN_REFERENCE §5.3, Phase 6). The whole
 * {@code /api/v1/admin/**} tree is ADMIN-only in the security chain; the acting
 * admin comes from the token and is passed down for the audit trail.
 *
 * <p>There is no DELETE for products or categories: order history must survive,
 * so archival is the only "delete" (ADR-009).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminProductController {

	private final AdminProductService productService;
	private final AdminImageService imageService;

	public AdminProductController(AdminProductService productService,
			AdminImageService imageService) {
		this.productService = productService;
		this.imageService = imageService;
	}

	@GetMapping("/products")
	public PageResponse<AdminProductSummaryResponse> list(@CurrentUser String adminId,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String categoryId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return productService.list(q, status, categoryId, page, size);
	}

	@PostMapping("/products")
	public ResponseEntity<ApiResponse<AdminProductDetailResponse>> create(
			@CurrentUser String adminId, @Valid @RequestBody CreateProductRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(productService.create(adminId, request)));
	}

	/**
	 * Creates a product and its first image in one multipart request, so the new
	 * product page can upload a photograph instead of requiring a save followed
	 * by a second trip to the editor. Every field except `file` arrives as a form
	 * part; `variants` is a JSON array inside the `product` part.
	 */
	@PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<AdminProductDetailResponse>> createWithImage(
			@CurrentUser String adminId,
			@Valid @RequestPart("product") CreateProductRequest request,
			@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestParam(required = false) String altText) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				productService.createWithImage(adminId, request, file, altText)));
	}

	@GetMapping("/products/{productId}")
	public ApiResponse<AdminProductDetailResponse> get(@CurrentUser String adminId,
			@PathVariable String productId) {
		return ApiResponse.success(productService.get(productId));
	}

	@PatchMapping("/products/{productId}")
	public ApiResponse<AdminProductDetailResponse> update(@CurrentUser String adminId,
			@PathVariable String productId, @Valid @RequestBody UpdateProductRequest request) {
		return ApiResponse.success(productService.update(adminId, productId, request));
	}

	@PostMapping("/products/{productId}/variants")
	public ResponseEntity<ApiResponse<AdminProductDetailResponse>> addVariant(
			@CurrentUser String adminId, @PathVariable String productId,
			@Valid @RequestBody VariantRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(productService.addVariant(adminId, productId, request)));
	}

	@PatchMapping("/variants/{variantId}")
	public ApiResponse<AdminVariantResponse> updateVariant(@CurrentUser String adminId,
			@PathVariable String variantId, @Valid @RequestBody UpdateVariantRequest request) {
		return ApiResponse.success(productService.updateVariant(adminId, variantId, request));
	}

	/** Absolute stock set; audited with before/after and an optional reason. */
	@PatchMapping("/variants/{variantId}/stock")
	public ApiResponse<AdminVariantResponse> setStock(@CurrentUser String adminId,
			@PathVariable String variantId, @Valid @RequestBody SetStockRequest request) {
		return ApiResponse.success(productService.setStock(adminId, variantId, request));
	}

	/** Multipart upload; the file type is sniffed, the name replaced with a UUID. */
	@PostMapping("/products/{productId}/images")
	public ResponseEntity<ApiResponse<List<AdminImageResponse>>> uploadImage(
			@CurrentUser String adminId, @PathVariable String productId,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String altText) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				imageService.upload(adminId, productId, file, altText)));
	}

	@DeleteMapping("/products/{productId}/images/{imageId}")
	public ResponseEntity<Void> deleteImage(@CurrentUser String adminId,
			@PathVariable String productId, @PathVariable String imageId) {
		imageService.delete(adminId, productId, imageId);
		return ResponseEntity.noContent().build();
	}
}

package clothing.app.domain.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.admin.dto.AdminCategoryRequests.CreateCategoryRequest;
import clothing.app.domain.admin.dto.AdminCategoryRequests.UpdateCategoryRequest;
import clothing.app.domain.admin.dto.AdminCategoryResponse;

/**
 * Admin category API (DESIGN_REFERENCE §5.3, Phase 6). The public
 * {@code GET /categories} remains active-only; this one shows every category
 * with its product count. Deactivation replaces deletion (ADR-009).
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

	private final AdminCategoryService categoryService;

	public AdminCategoryController(AdminCategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	public ApiResponse<List<AdminCategoryResponse>> list(@CurrentUser String adminId) {
		return ApiResponse.success(categoryService.list());
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AdminCategoryResponse>> create(@CurrentUser String adminId,
			@Valid @RequestBody CreateCategoryRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(categoryService.create(adminId, request)));
	}

	@PatchMapping("/{categoryId}")
	public ApiResponse<AdminCategoryResponse> update(@CurrentUser String adminId,
			@PathVariable String categoryId,
			@Valid @RequestBody UpdateCategoryRequest request) {
		return ApiResponse.success(categoryService.update(adminId, categoryId, request));
	}
}

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
import clothing.app.domain.admin.dto.AdminPromotionRequests.CreatePromotionRequest;
import clothing.app.domain.admin.dto.AdminPromotionRequests.UpdatePromotionRequest;
import clothing.app.domain.admin.dto.AdminPromotionResponse;

/**
 * Admin promotions (PROJECT_SPEC §6, §11: {@code /admin/promotions}). Deactivate
 * instead of deleting, like categories and products (ADR-009).
 */
@RestController
@RequestMapping("/api/v1/admin/promotions")
public class AdminPromotionController {

	private final AdminPromotionService promotionService;

	public AdminPromotionController(AdminPromotionService promotionService) {
		this.promotionService = promotionService;
	}

	@GetMapping
	public ApiResponse<List<AdminPromotionResponse>> list(@CurrentUser String adminId) {
		return ApiResponse.success(promotionService.list());
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AdminPromotionResponse>> create(
			@CurrentUser String adminId,
			@Valid @RequestBody CreatePromotionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(promotionService.create(adminId, request)));
	}

	@PatchMapping("/{promotionId}")
	public ApiResponse<AdminPromotionResponse> update(@CurrentUser String adminId,
			@PathVariable String promotionId,
			@Valid @RequestBody UpdatePromotionRequest request) {
		return ApiResponse.success(promotionService.update(adminId, promotionId, request));
	}
}

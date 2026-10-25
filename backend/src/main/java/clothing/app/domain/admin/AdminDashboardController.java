package clothing.app.domain.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;

/**
 * Dashboard summary (DESIGN_REFERENCE §5.3, Phase 6): counts and revenue, with
 * the revenue definition stated on {@link AdminDashboardService}.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

	private final AdminDashboardService dashboardService;

	public AdminDashboardController(AdminDashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/summary")
	public ApiResponse<AdminDashboardService.AdminDashboardSummary> summary(
			@CurrentUser String adminId) {
		return ApiResponse.success(dashboardService.summary());
	}
}

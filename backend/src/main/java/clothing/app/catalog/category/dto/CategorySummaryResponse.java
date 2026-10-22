package clothing.app.catalog.category.dto;

import clothing.app.domain.catalog.category.CategoryEntity;

public record CategorySummaryResponse(String id, String name, String slug) {

	public static CategorySummaryResponse from(CategoryEntity category) {
		return new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug());
	}
}

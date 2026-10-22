package clothing.app.catalog.category.dto;

import clothing.app.domain.catalog.category.CategoryEntity;

public record CategoryResponse(
	String id,
	String name,
	String slug,
	String description,
	String parentId,
	int sortOrder
) {

	public static CategoryResponse from(CategoryEntity category) {
		return new CategoryResponse(
			category.getId(),
			category.getName(),
			category.getSlug(),
			category.getDescription(),
			category.getParentId(),
			category.getSortOrder() == null ? 0 : category.getSortOrder()
		);
	}
}

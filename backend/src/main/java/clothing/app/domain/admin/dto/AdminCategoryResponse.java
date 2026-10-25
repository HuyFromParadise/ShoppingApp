package clothing.app.domain.admin.dto;

/**
 * A category as the admin sees it: unlike the public list this includes inactive
 * categories and the product count, because deactivating a category that still
 * holds products is a decision an operator must see the cost of.
 */
public record AdminCategoryResponse(
		String id,
		String name,
		String slug,
		String description,
		String parentId,
		boolean active,
		int sortOrder,
		long productCount) {
}

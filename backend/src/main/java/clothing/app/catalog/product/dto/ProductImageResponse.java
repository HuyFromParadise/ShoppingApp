package clothing.app.catalog.product.dto;

import clothing.app.domain.catalog.product.ProductImageEntity;

public record ProductImageResponse(
	String id,
	String url,
	String altText,
	int sortOrder,
	boolean primary
) {

	public static ProductImageResponse from(ProductImageEntity image) {
		return new ProductImageResponse(
			image.getId(),
			image.getStorageKey(),
			image.getAltText(),
			image.getSortOrder() == null ? 0 : image.getSortOrder(),
			Boolean.TRUE.equals(image.getIsPrimary())
		);
	}
}

package clothing.app.domain.admin;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import clothing.app.catalog.image.ImageStorage;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.admin.dto.AdminImageResponse;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;

/**
 * Product image uploads (DESIGN_REFERENCE §5.3, §6.3).
 *
 * <p>The file is validated and written to storage first, then the image row is
 * inserted in the same transaction. If the row insert fails the stored file is
 * deleted again, so a rejected upload never leaves an orphan behind.
 */
@Service
public class AdminImageService {

	private final ProductRepository productRepository;
	private final ProductImageRepository imageRepository;
	private final ImageStorage imageStorage;
	private final AuditService auditService;

	public AdminImageService(ProductRepository productRepository,
			ProductImageRepository imageRepository, ImageStorage imageStorage,
			AuditService auditService) {
		this.productRepository = productRepository;
		this.imageRepository = imageRepository;
		this.imageStorage = imageStorage;
		this.auditService = auditService;
	}

	@Transactional
	public List<AdminImageResponse> upload(String adminUserId, String productId, MultipartFile file,
			String altText) {
		ProductEntity product = productRepository.findById(productId)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));

		ImageStorage.StoredImage stored;
		try {
			stored = imageStorage.store(file);
		}
		catch (ImageStorage.ImageRejectedException ex) {
			throw new ApiException.Validation(ex.getMessage(), Map.of("file", ex.getMessage()));
		}

		try {
			List<ProductImageEntity> existing = imageRepository
				.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId());
			boolean first = existing.isEmpty();
			int sortOrder = existing.stream()
				.mapToInt(image -> image.getSortOrder() == null ? 0 : image.getSortOrder())
				.max()
				.orElse(-1) + 1;

			ProductImageEntity image = new ProductImageEntity();
			image.setProductId(product.getId());
			image.setStorageKey(stored.publicPath());
			image.setAltText(trimToMax(altText, product.getName(), 200));
			image.setSortOrder(sortOrder);
			image.setIsPrimary(first);
			imageRepository.save(image);

			auditService.record(adminUserId, "product.image.upload", "PRODUCT", product.getId(),
				Map.of("imageId", image.getId(), "url", stored.publicPath(),
					"bytes", stored.sizeBytes(), "primary", first));

			return imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId())
				.stream()
				.map(AdminImageService::toResponse)
				.toList();
		}
		catch (RuntimeException ex) {
			// The row did not commit, so its file must not stay on disk either.
			imageStorage.delete(stored.publicPath());
			throw ex;
		}
	}

	@Transactional
	public void delete(String adminUserId, String productId, String imageId) {
		ProductEntity product = productRepository.findById(productId)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));
		ProductImageEntity image = imageRepository.findById(imageId)
			.filter(candidate -> candidate.getProductId().equals(product.getId()))
			.orElseThrow(() -> new ApiException.NotFound("Image not found"));

		imageRepository.delete(image);
		// Promote the next image so a product with images always has a primary.
		if (Boolean.TRUE.equals(image.getIsPrimary())) {
			imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId())
				.stream()
				.filter(candidate -> !candidate.getId().equals(image.getId()))
				.findFirst()
				.ifPresent(next -> {
					next.setIsPrimary(Boolean.TRUE);
					imageRepository.save(next);
				});
		}
		imageStorage.delete(image.getStorageKey());
		auditService.record(adminUserId, "product.image.delete", "PRODUCT", product.getId(),
			Map.of("imageId", imageId));
	}

	private static String trimToMax(String value, String fallback, int max) {
		String chosen = value == null || value.isBlank() ? fallback : value.trim();
		if (chosen == null) {
			return null;
		}
		return chosen.length() <= max ? chosen : chosen.substring(0, max);
	}

	static AdminImageResponse toResponse(ProductImageEntity image) {
		return new AdminImageResponse(image.getId(), image.getStorageKey(), image.getAltText(),
			image.getSortOrder() == null ? 0 : image.getSortOrder(),
			Boolean.TRUE.equals(image.getIsPrimary()));
	}
}

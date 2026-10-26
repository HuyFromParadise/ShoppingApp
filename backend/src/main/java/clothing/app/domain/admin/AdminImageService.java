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
		ProductEntity product = requireProduct(productId);

		ImageStorage.StoredImage stored;
		try {
			stored = imageStorage.store(file);
		}
		catch (ImageStorage.ImageRejectedException ex) {
			throw new ApiException.Validation(ex.getMessage(), Map.of("file", ex.getMessage()));
		}

		return writeImageRow(adminUserId, product, stored, altText);
	}

	/**
	 * Stores one image for a product that was just created, so a new product can
	 * be saved with its first photograph in a single action instead of an
	 * otherwise pointless round trip to the edit page.
	 *
	 * <p>Called from inside the create transaction: if the row cannot be written,
	 * the caller persists nothing and the file is deleted again, so neither a
	 * product without its image nor an orphaned file is left behind.
	 */
	@Transactional
	public void attachToNewProduct(String adminUserId, ProductEntity product, MultipartFile file,
			String altText) {
		if (file == null || file.isEmpty()) {
			return;
		}
		ImageStorage.StoredImage stored;
		try {
			stored = imageStorage.store(file);
		}
		catch (ImageStorage.ImageRejectedException ex) {
			throw new ApiException.Validation(ex.getMessage(), Map.of("file", ex.getMessage()));
		}
		writeImageRow(adminUserId, product, stored, altText);
	}

	/**
	 * Validates and stores a file, without touching the database.
	 *
	 * <p>Separating the two halves lets the create path finish all validation
	 * before it persists anything, so a rejected file cannot leave a product
	 * behind, while the row and the file stay in one transaction.
	 */
	public ImageStorage.StoredImage storeOrReject(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException.Validation("Choose an image file",
				Map.of("file", "Choose an image file"));
		}
		try {
			return imageStorage.store(file);
		}
		catch (ImageStorage.ImageRejectedException ex) {
			throw new ApiException.Validation(ex.getMessage(), Map.of("file", ex.getMessage()));
		}
	}

	/** Writes the row for an already-stored file. */
	@Transactional
	public void attachStored(String adminUserId, ProductEntity product,
			ImageStorage.StoredImage stored, String altText) {
		writeImageRow(adminUserId, product, stored, altText);
	}

	/** Deletes a stored file whose row was not written. */
	public void discard(ImageStorage.StoredImage stored) {
		if (stored != null) {
			imageStorage.delete(stored.publicPath());
		}
	}

	/**
	 * Writes the image row and its audit entry. Shared by the standalone upload
	 * and the create-time upload so both behave identically: first image becomes
	 * primary, sort order continues, size and type already checked by storage.
	 */
	private List<AdminImageResponse> writeImageRow(String adminUserId, ProductEntity product,
			ImageStorage.StoredImage stored, String altText) {
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
		ProductEntity product = requireProduct(productId);
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

	private ProductEntity requireProduct(String productId) {
		return productRepository.findById(productId)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));
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

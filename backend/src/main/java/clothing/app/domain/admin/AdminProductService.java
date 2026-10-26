package clothing.app.domain.admin;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.image.ImageStorage;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.common.response.PageResponse;
import clothing.app.domain.admin.dto.AdminImageResponse;
import clothing.app.domain.admin.dto.AdminProductDetailResponse;
import clothing.app.domain.admin.dto.AdminProductRequests.CreateProductRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.SetStockRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.UpdateProductRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.UpdateVariantRequest;
import clothing.app.domain.admin.dto.AdminProductRequests.VariantRequest;
import clothing.app.domain.admin.dto.AdminProductSummaryResponse;
import clothing.app.domain.admin.dto.AdminVariantResponse;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

/**
 * Admin catalog management (DESIGN_REFERENCE §5.3, §6.2; Journey E).
 *
 * <p>Two rules shape everything here:
 * <ul>
 *   <li><b>Archival, never deletion</b> (ADR-009): order history references
 *       products, so the "delete" an operator can perform is
 *       {@code ARCHIVED} / {@code is_active = false}.</li>
 *   <li><b>Audit the sensitive changes</b>: stock is set absolutely and every
 *       change is recorded in {@code audit_events} with the acting admin.</li>
 * </ul>
 *
 * <p>Controllers pass the admin's id from the token; it is never read from the
 * body, so an admin cannot forge another actor in the audit log.
 */
@Service
public class AdminProductService {

	static final int MAX_PAGE_SIZE = 48;
	private static final int MAX_SLUG_ATTEMPTS = 5;
	private static final Pattern SLUG_INVALID = Pattern.compile("[^a-z0-9]+");
	private static final Pattern SLUG_EDGES = Pattern.compile("^-+|-+$");

	private final ProductRepository productRepository;
	private final ProductVariantRepository variantRepository;
	private final ProductImageRepository imageRepository;
	private final CategoryRepository categoryRepository;
	private final AuditService auditService;
	private final AdminImageService imageService;

	public AdminProductService(ProductRepository productRepository,
			ProductVariantRepository variantRepository,
			ProductImageRepository imageRepository,
			CategoryRepository categoryRepository, AuditService auditService,
			AdminImageService imageService) {
		this.productRepository = productRepository;
		this.variantRepository = variantRepository;
		this.imageRepository = imageRepository;
		this.categoryRepository = categoryRepository;
		this.auditService = auditService;
		this.imageService = imageService;
	}

	/* --- reads -------------------------------------------------------------- */

	/**
	 * Every product regardless of status, optionally filtered. Filters are applied
	 * in memory over one page because the catalog is small and the admin list is
	 * not a hot path; a filtered SQL query arrives with the admin UI step if the
	 * catalog outgrows it.
	 */
	@Transactional(readOnly = true)
	public PageResponse<AdminProductSummaryResponse> list(String q, String status, String categoryId,
			int page, int size) {
		validatePage(page, size);
		PageRequest pageable = PageRequest.of(page, size,
			Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<ProductEntity> products = productRepository.findAll(pageable);

		Map<String, CategoryEntity> categories = categoriesById(products.getContent());
		String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
		ProductEntity.Status statusFilter = parseStatus(status);

		List<AdminProductSummaryResponse> rows = products.getContent().stream()
			.filter(product -> statusFilter == null || product.getStatus() == statusFilter)
			.filter(product -> categoryId == null
				|| categoryId.isBlank() || categoryId.equals(product.getCategoryId()))
			.filter(product -> needle == null || needle.isEmpty()
				|| product.getName().toLowerCase(Locale.ROOT).contains(needle)
				|| product.getSlug().contains(needle))
			.map(product -> toSummary(product, categories.get(product.getCategoryId())))
			.toList();
		return PageResponse.of(rows, page, size, products.getTotalElements());
	}

	@Transactional(readOnly = true)
	public AdminProductDetailResponse get(String productId) {
		ProductEntity product = requireProduct(productId);
		return toDetail(product, categoryOf(product.getCategoryId()).orElse(null));
	}

	/* --- writes ------------------------------------------------------------- */

	@Transactional
	public AdminProductDetailResponse create(String adminUserId, CreateProductRequest request) {
		ProductEntity product = insertProduct(adminUserId, request);
		return toDetail(product, categoryOf(product.getCategoryId()).orElse(null));
	}

	/**
	 * Validates and writes one product with its variants, and records the audit
	 * entry. Kept separate from {@link #create} so the multipart create can write
	 * the image row in the same unit of work without nesting transactional calls.
	 */
	private ProductEntity insertProduct(String adminUserId, CreateProductRequest request) {
		CategoryEntity category = requireCategory(request.categoryId());
		String slug = StringUtils.hasText(request.slug())
			? request.slug().trim()
			: uniqueSlug(request.name());
		if (slugExists(slug)) {
			throw new ApiException.Validation("Slug is already used by another product",
				Map.of("slug", "Already in use"));
		}
		validateVariantCombinations(request.variants(), null);

		ProductEntity product = new ProductEntity();
		product.setName(request.name().trim());
		product.setSlug(slug);
		product.setShortDescription(trimToNull(request.shortDescription()));
		product.setDescription(trimToNull(request.description()));
		product.setCategoryId(category.getId());
		product.setBasePrice(request.basePrice());
		product.setCurrency(request.currency() == null ? "VND" : request.currency());
		product.setStatus(request.status() == null
			? ProductEntity.Status.DRAFT
			: ProductEntity.Status.valueOf(request.status()));
		product.setMaterial(trimToNull(request.material()));
		product.setFit(trimToNull(request.fit()));
		product.setCareInstructions(trimToNull(request.careInstructions()));
		product.setFeatured(Boolean.TRUE.equals(request.featured()));
		product = productRepository.save(product);

		List<ProductVariantEntity> variants = new ArrayList<>();
		for (VariantRequest variantRequest : request.variants()) {
			variants.add(newVariant(product, variantRequest));
		}
		variantRepository.saveAll(variants);

		auditService.record(adminUserId, "product.create", "PRODUCT", product.getId(),
			Map.of("slug", slug, "status", product.getStatus().name(),
				"variantCount", variants.size()));
		return product;
	}

	@Transactional
	public AdminProductDetailResponse update(String adminUserId, String productId,
			UpdateProductRequest request) {
		ProductEntity product = requireProduct(productId);
		Map<String, Object> changed = new LinkedHashMap<>();

		if (request.categoryId() != null && !request.categoryId().isBlank()
				&& !request.categoryId().equals(product.getCategoryId())) {
			CategoryEntity category = requireCategory(request.categoryId());
			changed.put("categoryId", pair(product.getCategoryId(), category.getId()));
			product.setCategoryId(category.getId());
		}
		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new ApiException.Validation("Name cannot be blank",
					Map.of("name", "Name is required"));
			}
			if (!name.equals(product.getName())) {
				changed.put("name", pair(product.getName(), name));
				product.setName(name);
			}
		}
		if (request.shortDescription() != null) {
			product.setShortDescription(trimToNull(request.shortDescription()));
		}
		if (request.description() != null) {
			product.setDescription(trimToNull(request.description()));
		}
		if (request.basePrice() != null && !request.basePrice().equals(product.getBasePrice())) {
			changed.put("basePrice", pair(product.getBasePrice(), request.basePrice()));
			product.setBasePrice(request.basePrice());
		}
		if (request.material() != null) {
			product.setMaterial(trimToNull(request.material()));
		}
		if (request.fit() != null) {
			product.setFit(trimToNull(request.fit()));
		}
		if (request.careInstructions() != null) {
			product.setCareInstructions(trimToNull(request.careInstructions()));
		}
		if (request.featured() != null && !request.featured().equals(product.getFeatured())) {
			changed.put("featured", pair(product.getFeatured(), request.featured()));
			product.setFeatured(request.featured());
		}
		if (request.status() != null) {
			ProductEntity.Status status = ProductEntity.Status.valueOf(request.status());
			if (status != product.getStatus()) {
				changed.put("status", pair(product.getStatus().name(), status.name()));
				product.setStatus(status);
			}
		}

		productRepository.save(product);
		if (!changed.isEmpty()) {
			auditService.record(adminUserId, "product.update", "PRODUCT", product.getId(),
				Map.of("changes", changed));
		}
		return toDetail(product, categoryOf(product.getCategoryId()).orElse(null));
	}

	/**
	 * Creates a product together with its first image (and optional alt text).
	 *
	 * <p>The image is validated and stored before anything is persisted: a
	 * rejected file must not leave a half-created product behind, so the whole
	 * call fails with the file's reason and nothing is written.
	 */
	@Transactional
	public AdminProductDetailResponse createWithImage(String adminUserId, CreateProductRequest request,
			MultipartFile image, String altText) {
		if (image == null || image.isEmpty()) {
			return create(adminUserId, request);
		}
		// The file is validated and stored BEFORE the product row is written. A
		// rejected file then fails the request with nothing to roll back, which
		// matters because {@link #create} joins this transaction and its
		// transactional boundaries are method calls, not proxies — a rollback
		// there would not undo an insert already made here.
		ImageStorage.StoredImage stored = imageService.storeOrReject(image);
		try {
			ProductEntity product = insertProduct(adminUserId, request);
			imageService.attachStored(adminUserId, product, stored, altText);
			return toDetail(product, categoryOf(product.getCategoryId()).orElse(null));
		}
		catch (RuntimeException ex) {
			imageService.discard(stored);
			throw ex;
		}
	}
	/* --- variants ----------------------------------------------------------- */

	@Transactional
	public AdminProductDetailResponse addVariant(String adminUserId, String productId,
			VariantRequest request) {
		ProductEntity product = requireProduct(productId);
		validateVariantCombinations(List.of(request), product.getId());
		ProductVariantEntity variant = variantRepository.save(newVariant(product, request));
		auditService.record(adminUserId, "variant.create", "PRODUCT_VARIANT", variant.getId(),
			Map.of("productId", product.getId(), "sku", variant.getSku(),
				"stockQuantity", variant.getStockQuantity()));
		return toDetail(product, categoryOf(product.getCategoryId()).orElse(null));
	}

	@Transactional
	public AdminVariantResponse updateVariant(String adminUserId, String variantId,
			UpdateVariantRequest request) {
		ProductVariantEntity variant = requireVariant(variantId);
		Map<String, Object> changed = new LinkedHashMap<>();

		if (request.sku() != null) {
			String sku = request.sku().trim();
			if (!sku.equals(variant.getSku())) {
				if (variantRepository.existsBySkuAndIdNot(sku, variant.getId())) {
					throw new ApiException.Validation("SKU is already used",
						Map.of("sku", "Already in use"));
				}
				changed.put("sku", pair(variant.getSku(), sku));
				variant.setSku(sku);
			}
		}
		if (request.size() != null) {
			changed.put("size", pair(variant.getSize(), trimToNull(request.size())));
			variant.setSize(trimToNull(request.size()));
		}
		if (request.color() != null) {
			changed.put("color", pair(variant.getColor(), trimToNull(request.color())));
			variant.setColor(trimToNull(request.color()));
		}
		if (request.priceOverride() != null) {
			changed.put("priceOverride",
				pair(variant.getPriceOverride(), request.priceOverride()));
			variant.setPriceOverride(request.priceOverride());
		}
		if (request.active() != null && !request.active().equals(variant.getIsActive())) {
			changed.put("active", pair(variant.getIsActive(), request.active()));
			variant.setIsActive(request.active());
		}

		// A changed size/colour pair must not collide with a sibling variant.
		if (changed.containsKey("size") || changed.containsKey("color")) {
			if (variantRepository.existsByProductIdAndSizeAndColorAndIdNot(
					variant.getProductId(), variant.getSize(), variant.getColor(),
					variant.getId())) {
				throw new ApiException.Conflict(
					"Another variant of this product already has that size and colour");
			}
		}

		variantRepository.save(variant);
		if (!changed.isEmpty()) {
			auditService.record(adminUserId, "variant.update", "PRODUCT_VARIANT", variant.getId(),
				Map.of("changes", changed));
		}
		return toVariantResponse(variant);
	}

	/**
	 * Absolute stock set (DESIGN_REFERENCE §5.3). Read-then-write under a row
	 * lock so two admins cannot lose one of the two updates, and audited with the
	 * before/after values and an optional reason.
	 */
	@Transactional
	public AdminVariantResponse setStock(String adminUserId, String variantId,
			SetStockRequest request) {
		ProductVariantEntity variant = variantRepository.findByIdForUpdate(variantId)
			.orElseThrow(() -> new ApiException.NotFound("Variant not found"));
		int previous = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
		int next = request.stockQuantity();
		if (previous == next) {
			return toVariantResponse(variant);
		}
		variant.setStockQuantity(next);
		variantRepository.save(variant);

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("field", "stockQuantity");
		metadata.put("from", previous);
		metadata.put("to", next);
		metadata.put("productId", variant.getProductId());
		if (StringUtils.hasText(request.reason())) {
			metadata.put("reason", request.reason().trim());
		}
		auditService.record(adminUserId, "variant.stock.set", "PRODUCT_VARIANT", variant.getId(),
			metadata);
		return toVariantResponse(variant);
	}

	/* --- rules -------------------------------------------------------------- */

	/**
	 * {@code UNIQUE (product_id, size, color)} would surface as a 500 from the
	 * database; checking first turns it into a field error the admin can act on.
	 */
	private void validateVariantCombinations(List<VariantRequest> variants, String productId) {
		Set<String> seen = new HashSet<>();
		List<String> fieldErrors = new ArrayList<>();
		for (VariantRequest variant : variants) {
			String key = combination(variant.size(), variant.color());
			if (!seen.add(key)) {
				fieldErrors.add("A variant with size " + show(variant.size()) + " and colour "
					+ show(variant.color()) + " is listed twice");
			}
			if (productId != null
					&& variantRepository.existsByProductIdAndSizeAndColor(
						productId, trimToNull(variant.size()), trimToNull(variant.color()))) {
				fieldErrors.add("A variant with size " + show(variant.size()) + " and colour "
					+ show(variant.color()) + " already exists");
			}
		}
		if (!fieldErrors.isEmpty()) {
			throw new ApiException.Validation(String.join("; ", fieldErrors),
				Map.of("variants", fieldErrors.get(0)));
		}
	}

	/**
	 * A before/after pair for the audit metadata. {@code List.of} rejects null,
	 * and half of these pairs legitimately start empty (no price override yet, no
	 * size on a one-size item), so a null-tolerant pair is used instead.
	 */
	private static List<Object> pair(Object before, Object after) {
		List<Object> values = new ArrayList<>(2);
		values.add(before);
		values.add(after);
		return values;
	}
	private static String combination(String size, String color) {
		return show(size) + "|" + show(color);
	}

	private static String show(String value) {
		return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "-";
	}

	private ProductVariantEntity newVariant(ProductEntity product, VariantRequest request) {
		ProductVariantEntity variant = new ProductVariantEntity();
		variant.setProductId(product.getId());
		variant.setSku(StringUtils.hasText(request.sku()) ? request.sku().trim()
			: generatedSku(product, request));
		variant.setSize(trimToNull(request.size()));
		variant.setColor(trimToNull(request.color()));
		variant.setPriceOverride(request.priceOverride());
		variant.setStockQuantity(request.stockQuantity());
		variant.setIsActive(request.active() == null || request.active());
		return variant;
	}

	private String generatedSku(ProductEntity product, VariantRequest request) {
		// SKUs read as data in a warehouse: upper case, no lowercase fragments.
		String base = product.getSlug().toUpperCase(Locale.ROOT).replace('-', '_');
		String suffix = show(request.color()).replaceAll("[^a-z0-9]+", "")
			+ "-" + show(request.size()).replaceAll("[^a-z0-9]+", "");
		String candidate = base + "-" + suffix.toUpperCase(Locale.ROOT);
		if (candidate.length() > 64) {
			candidate = candidate.substring(0, 64);
		}
		// SKUs are unique; a clash appends a short random tail.
		return variantRepository.existsBySku(candidate)
			? candidate.substring(0, Math.min(55, candidate.length())) + "-"
				+ UUID.randomUUID().toString().substring(0, 8)
			: candidate;
	}

	private String uniqueSlug(String name) {
		String base = slugify(name);
		String candidate = base;
		int attempt = 1;
		while (slugExists(candidate)) {
			attempt++;
			if (attempt > MAX_SLUG_ATTEMPTS) {
				candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
				break;
			}
			candidate = base + "-" + attempt;
		}
		return candidate;
	}

	/** A slug for a product name; falls back to a random one when nothing usable remains. */
	static String slugify(String name) {
		String slug = toSlug(name);
		return slug.isEmpty() ? "item-" + UUID.randomUUID().toString().substring(0, 8) : slug;
	}

	/**
	 * Shared by product and category slugs so both obey the same URL shape.
	 * Accents are folded where Unicode can (NFD); the Vietnamese D-with-stroke is
	 * folded by hand because NFD leaves it intact. A name with nothing URL-safe
	 * left returns an empty string; the caller decides whether that is an error
	 * or a generated slug.
	 */
	static String toSlug(String name) {
		String normalized = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.replace('\u0111', 'd')
			.replace('\u0110', 'D')
			.toLowerCase(Locale.ROOT);
		return SLUG_EDGES.matcher(SLUG_INVALID.matcher(normalized).replaceAll("-")).replaceAll("");
	}

	private boolean slugExists(String slug) {
		return productRepository.existsBySlug(slug);
	}

	/* --- helpers ------------------------------------------------------------ */

	private ProductEntity requireProduct(String productId) {
		return productRepository.findById(productId)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));
	}

	private ProductVariantEntity requireVariant(String variantId) {
		return variantRepository.findById(variantId)
			.orElseThrow(() -> new ApiException.NotFound("Variant not found"));
	}

	private CategoryEntity requireCategory(String categoryId) {
		return categoryRepository.findById(categoryId)
			.orElseThrow(() -> new ApiException.Validation("Category not found",
				Map.of("categoryId", "Unknown category")));
	}

	private Optional<CategoryEntity> categoryOf(String categoryId) {
		return categoryRepository.findById(categoryId);
	}

	private Map<String, CategoryEntity> categoriesById(List<ProductEntity> products) {
		List<String> ids = products.stream().map(ProductEntity::getCategoryId).distinct().toList();
		Map<String, CategoryEntity> result = new LinkedHashMap<>();
		for (CategoryEntity category : categoryRepository.findAllById(ids)) {
			result.put(category.getId(), category);
		}
		return result;
	}

	private ProductEntity.Status parseStatus(String status) {
		if (!StringUtils.hasText(status)) {
			return null;
		}
		try {
			return ProductEntity.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException.Validation("Unknown product status",
				Map.of("status", "Must be DRAFT, ACTIVE or ARCHIVED"));
		}
	}

	private AdminProductSummaryResponse toSummary(ProductEntity product, CategoryEntity category) {
		List<ProductVariantEntity> variants = variantRepository
			.findByProductIdOrderBySizeAscColorAsc(product.getId());
		List<ProductImageEntity> images = imageRepository
			.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId());
		return new AdminProductSummaryResponse(
			product.getId(),
			product.getName(),
			product.getSlug(),
			product.getCategoryId(),
			category == null ? null : category.getName(),
			product.getBasePrice(),
			product.getCurrency(),
			product.getStatus().name(),
			Boolean.TRUE.equals(product.getFeatured()),
			variants.size(),
			variants.stream()
				.mapToInt(variant -> variant.getStockQuantity() == null ? 0
					: variant.getStockQuantity())
				.sum(),
			images.isEmpty() ? null : images.get(0).getStorageKey(),
			product.getCreatedAt(),
			product.getUpdatedAt());
	}

	private AdminProductDetailResponse toDetail(ProductEntity product, CategoryEntity category) {
		List<ProductVariantEntity> variants = variantRepository
			.findByProductIdOrderBySizeAscColorAsc(product.getId());
		List<ProductImageEntity> images = imageRepository
			.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId());
		return new AdminProductDetailResponse(
			product.getId(),
			product.getName(),
			product.getSlug(),
			product.getShortDescription(),
			product.getDescription(),
			product.getCategoryId(),
			category == null ? null : category.getName(),
			product.getBasePrice(),
			product.getCurrency(),
			product.getStatus().name(),
			product.getMaterial(),
			product.getFit(),
			product.getCareInstructions(),
			Boolean.TRUE.equals(product.getFeatured()),
			images.stream()
				.sorted(Comparator.comparing(ProductImageEntity::getSortOrder))
				.map(image -> new AdminImageResponse(image.getId(), image.getStorageKey(),
					image.getAltText(), image.getSortOrder(),
					Boolean.TRUE.equals(image.getIsPrimary())))
				.toList(),
			variants.stream().map(this::toVariantResponse).toList(),
			product.getCreatedAt(),
			product.getUpdatedAt());
	}

	private AdminVariantResponse toVariantResponse(ProductVariantEntity variant) {
		long basePrice = productRepository.findById(variant.getProductId())
			.map(ProductEntity::getBasePrice)
			.orElse(0L);
		long effective = variant.getPriceOverride() != null ? variant.getPriceOverride()
			: basePrice;
		return new AdminVariantResponse(
			variant.getId(),
			variant.getSku(),
			variant.getSize(),
			variant.getColor(),
			variant.getPriceOverride(),
			effective,
			variant.getStockQuantity() == null ? 0 : variant.getStockQuantity(),
			Boolean.TRUE.equals(variant.getIsActive()));
	}

	static void validatePage(int page, int size) {
		if (page < 0) {
			throw new ApiException.Validation("Page must be zero or greater",
				Map.of("page", "Must be 0 or greater"));
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException.Validation("Page size must be between 1 and " + MAX_PAGE_SIZE,
				Map.of("size", "Must be between 1 and " + MAX_PAGE_SIZE));
		}
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}

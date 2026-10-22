package clothing.app.catalog.product;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.category.dto.CategorySummaryResponse;
import clothing.app.catalog.product.dto.ProductDetailResponse;
import clothing.app.catalog.product.dto.ProductImageResponse;
import clothing.app.catalog.product.dto.ProductSummaryResponse;
import clothing.app.catalog.product.dto.ProductVariantResponse;
import clothing.app.common.exception.ApiException;
import clothing.app.common.response.PageResponse;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

@Service
public class ProductService {

	private final ProductRepository productRepository;
	private final ProductImageRepository imageRepository;
	private final ProductVariantRepository variantRepository;
	private final CategoryRepository categoryRepository;

	public ProductService(
		ProductRepository productRepository,
		ProductImageRepository imageRepository,
		ProductVariantRepository variantRepository,
		CategoryRepository categoryRepository) {
		this.productRepository = productRepository;
		this.imageRepository = imageRepository;
		this.variantRepository = variantRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductSummaryResponse> list(ProductQuery query) {
		validatePriceRange(query);

		String categoryId = null;
		if (StringUtils.hasText(query.getCategory())) {
			categoryId = categoryRepository
				.findBySlugAndIsActiveTrue(query.getCategory().trim())
				.map(CategoryEntity::getId)
				.orElse(null);
			if (categoryId == null) {
				return emptyPage(query);
			}
		}
		return listInCategory(query, categoryId);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductSummaryResponse> listForCategory(String slug, ProductQuery query) {
		validatePriceRange(query);
		CategoryEntity category = categoryRepository.findBySlugAndIsActiveTrue(slug)
			.orElseThrow(() -> new ApiException.NotFound("Category not found"));
		return listInCategory(query, category.getId());
	}

	@Transactional(readOnly = true)
	public ProductDetailResponse getBySlug(String slug) {
		ProductEntity product = productRepository
			.findBySlugAndStatus(slug, ProductEntity.Status.ACTIVE)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));

		CategoryEntity category = categoryRepository
			.findByIdAndIsActiveTrue(product.getCategoryId())
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));

		List<ProductImageEntity> images = imageRepository
			.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId());
		List<ProductVariantEntity> variants = variantRepository
			.findByProductIdAndIsActiveTrueOrderBySizeAscColorAsc(product.getId());

		Price price = priceOf(product, variants);
		boolean inStock = variants.stream().anyMatch(this::hasStock);

		return new ProductDetailResponse(
			product.getId(),
			product.getName(),
			product.getSlug(),
			product.getShortDescription(),
			product.getDescription(),
			CategorySummaryResponse.from(category),
			product.getBasePrice(),
			price.current(),
			price.compareAt(),
			product.getCurrency(),
			price.onSale(),
			inStock,
			Boolean.TRUE.equals(product.getFeatured()),
			product.getMaterial(),
			product.getFit(),
			product.getCareInstructions(),
			images.stream().map(ProductImageResponse::from).toList(),
			variants.stream().map(v -> variantResponse(product, v)).toList(),
			product.getCreatedAt(),
			product.getUpdatedAt()
		);
	}

	private PageResponse<ProductSummaryResponse> listInCategory(ProductQuery query, String categoryId) {
		PageRequest pageable = PageRequest.of(
			query.getPage(),
			query.getSize(),
			ProductSort.fromKey(query.getSort() == null ? "newest" : query.getSort()).getSort());

		Page<ProductEntity> page = productRepository.findAll(
			ProductSpecifications.publicCatalog(query, categoryId), pageable);

		List<ProductSummaryResponse> items = toSummaries(page.getContent());
		return PageResponse.of(items, page.getNumber(), page.getSize(), page.getTotalElements());
	}

	private PageResponse<ProductSummaryResponse> emptyPage(ProductQuery query) {
		return PageResponse.of(List.of(), query.getPage(), query.getSize(), 0);
	}

	private List<ProductSummaryResponse> toSummaries(List<ProductEntity> products) {
		if (products.isEmpty()) {
			return List.of();
		}

		List<String> ids = products.stream().map(ProductEntity::getId).toList();
		Map<String, List<ProductImageEntity>> imagesByProduct = group(
			imageRepository.findByProductIdInOrderByIsPrimaryDescSortOrderAsc(ids),
			ProductImageEntity::getProductId);
		Map<String, List<ProductVariantEntity>> variantsByProduct = group(
			variantRepository.findByProductIdInAndIsActiveTrueOrderBySizeAscColorAsc(ids),
			ProductVariantEntity::getProductId);

		List<String> categoryIds = products.stream()
			.map(ProductEntity::getCategoryId)
			.distinct()
			.toList();
		Map<String, CategoryEntity> categories = categoryRepository.findAllById(categoryIds)
			.stream()
			.collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));

		return products.stream().map(product -> {
			List<ProductVariantEntity> variants = variantsByProduct.getOrDefault(product.getId(), List.of());
			List<ProductImageEntity> images = imagesByProduct.getOrDefault(product.getId(), List.of());
			Price price = priceOf(product, variants);
			CategoryEntity category = categories.get(product.getCategoryId());
			CategorySummaryResponse categorySummary = category == null
				? new CategorySummaryResponse(product.getCategoryId(), "Uncategorised", "")
				: CategorySummaryResponse.from(category);

			return new ProductSummaryResponse(
				product.getId(),
				product.getName(),
				product.getSlug(),
				product.getShortDescription(),
				categorySummary,
				price.current(),
				price.compareAt(),
				product.getCurrency(),
				price.onSale(),
				variants.stream().anyMatch(this::hasStock),
				Boolean.TRUE.equals(product.getFeatured()),
				images.isEmpty() ? null : ProductImageResponse.from(images.get(0)),
				product.getCreatedAt()
			);
		}).toList();
	}

	private ProductVariantResponse variantResponse(ProductEntity product, ProductVariantEntity variant) {
		boolean inStock = hasStock(variant);
		long price = variant.getPriceOverride() != null
			? variant.getPriceOverride()
			: product.getBasePrice();
		Long compareAt = variant.getPriceOverride() != null
			&& variant.getPriceOverride() < product.getBasePrice()
				? product.getBasePrice()
				: null;

		return new ProductVariantResponse(
			variant.getId(),
			variant.getSku(),
			variant.getSize(),
			variant.getColor(),
			price,
			compareAt,
			variant.getStockQuantity() == null ? 0 : variant.getStockQuantity(),
			inStock
		);
	}

	private Price priceOf(ProductEntity product, List<ProductVariantEntity> variants) {
		long base = product.getBasePrice();
		long current = variants.stream()
			.mapToLong(variant -> variant.getPriceOverride() == null
				? base
				: variant.getPriceOverride())
			.min()
			.orElse(base);
		return new Price(current, current < base ? base : null, current < base);
	}

	private boolean hasStock(ProductVariantEntity variant) {
		return variant.getStockQuantity() != null && variant.getStockQuantity() > 0;
	}

	private void validatePriceRange(ProductQuery query) {
		if (query.getMinPrice() != null && query.getMaxPrice() != null
			&& query.getMinPrice() > query.getMaxPrice()) {
			throw new ApiException.Validation(
				"minPrice must be less than or equal to maxPrice",
				Map.of("minPrice", "Must be less than or equal to maxPrice"));
		}
	}

	private static <T> Map<String, List<T>> group(
		Collection<T> values, Function<T, String> key) {
		return values.stream().collect(Collectors.groupingBy(key));
	}

	private record Price(long current, Long compareAt, boolean onSale) {
	}
}

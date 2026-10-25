package clothing.app.domain.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.admin.dto.AdminCategoryRequests.CreateCategoryRequest;
import clothing.app.domain.admin.dto.AdminCategoryResponse;
import clothing.app.domain.admin.dto.AdminCategoryRequests.UpdateCategoryRequest;
import clothing.app.domain.catalog.category.CategoryEntity;

/**
 * Admin category management (DESIGN_REFERENCE §5.3; ADR-009).
 *
 * <p>Categories are never deleted: deactivating one hides it from the storefront
 * while keeping the products and order history attached to it intact. A
 * parent-child cycle is rejected, and a category can only become its own
 * ancestor through a cycle, so that check is what keeps the tree a tree.
 */
@Service
public class AdminCategoryService {

	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final AuditService auditService;

	public AdminCategoryService(CategoryRepository categoryRepository,
			ProductRepository productRepository, AuditService auditService) {
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<AdminCategoryResponse> list() {
		return categoryRepository.findAll(Sort.by("sortOrder").ascending().and(Sort.by("name")))
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public AdminCategoryResponse create(String adminUserId, CreateCategoryRequest request) {
		String slug = StringUtils.hasText(request.slug())
			? request.slug().trim()
			: AdminProductService.toSlug(request.name());
		if (!StringUtils.hasText(slug)) {
			throw new ApiException.Validation("Slug is required", Map.of("slug", "Required"));
		}
		if (categoryRepository.findBySlug(slug).isPresent()) {
			throw new ApiException.Validation("Slug is already used by another category",
				Map.of("slug", "Already in use"));
		}
		String parentId = trimToNull(request.parentId());
		if (parentId != null) {
			requireCategory(parentId);
		}

		CategoryEntity category = new CategoryEntity();
		category.setName(request.name().trim());
		category.setSlug(slug);
		category.setDescription(trimToNull(request.description()));
		category.setParentId(parentId);
		category.setIsActive(request.active() == null || request.active());
		category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
		category = categoryRepository.save(category);

		auditService.record(adminUserId, "category.create", "CATEGORY", category.getId(),
			Map.of("slug", slug, "active", category.getIsActive()));
		return toResponse(category);
	}

	@Transactional
	public AdminCategoryResponse update(String adminUserId, String categoryId,
			UpdateCategoryRequest request) {
		CategoryEntity category = requireCategory(categoryId);
		Map<String, Object> changed = new LinkedHashMap<>();

		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new ApiException.Validation("Name cannot be blank",
					Map.of("name", "Name is required"));
			}
			if (!name.equals(category.getName())) {
				changed.put("name", List.of(category.getName(), name));
				category.setName(name);
			}
		}
		if (request.description() != null) {
			category.setDescription(trimToNull(request.description()));
		}
		if (request.parentId() != null) {
			String parentId = trimToNull(request.parentId());
			if (parentId != null) {
				rejectCycle(category.getId(), parentId);
			}
			if (!Objects.equals(parentId, category.getParentId())) {
				changed.put("parentId", List.of(String.valueOf(category.getParentId()),
					String.valueOf(parentId)));
				category.setParentId(parentId);
			}
		}
		if (request.active() != null && !request.active().equals(category.getIsActive())) {
			changed.put("active", List.of(category.getIsActive(), request.active()));
			category.setIsActive(request.active());
		}
		if (request.sortOrder() != null && !request.sortOrder().equals(category.getSortOrder())) {
			changed.put("sortOrder", List.of(category.getSortOrder(), request.sortOrder()));
			category.setSortOrder(request.sortOrder());
		}

		categoryRepository.save(category);
		if (!changed.isEmpty()) {
			auditService.record(adminUserId, "category.update", "CATEGORY", category.getId(),
				Map.of("changes", changed));
		}
		return toResponse(category);
	}

	/* --- rules -------------------------------------------------------------- */

	/**
	 * Walks up from the proposed parent. Meeting this category again means the
	 * move would create a cycle; a depth cap stops a corrupt chain from looping.
	 */
	private void rejectCycle(String categoryId, String parentId) {
		String cursor = parentId;
		int depth = 0;
		while (cursor != null) {
			if (cursor.equals(categoryId)) {
				throw new ApiException.Validation(
					"A category cannot be its own ancestor",
					Map.of("parentId", "Would create a cycle"));
			}
			if (++depth > 64) {
				throw new ApiException.Validation("Category hierarchy is too deep",
					Map.of("parentId", "Too deep"));
			}
			CategoryEntity parent = categoryRepository.findById(cursor).orElse(null);
			if (parent == null) {
				throw new ApiException.Validation("Parent category not found",
					Map.of("parentId", "Unknown category"));
			}
			cursor = parent.getParentId();
		}
	}

	private CategoryEntity requireCategory(String categoryId) {
		return categoryRepository.findById(categoryId)
			.orElseThrow(() -> new ApiException.NotFound("Category not found"));
	}

	private AdminCategoryResponse toResponse(CategoryEntity category) {
		long productCount = productRepository.countByCategoryId(category.getId());
		return new AdminCategoryResponse(
			category.getId(),
			category.getName(),
			category.getSlug(),
			category.getDescription(),
			category.getParentId(),
			Boolean.TRUE.equals(category.getIsActive()),
			category.getSortOrder() == null ? 0 : category.getSortOrder(),
			productCount);
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}

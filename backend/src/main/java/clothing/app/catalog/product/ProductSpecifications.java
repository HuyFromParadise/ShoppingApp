package clothing.app.catalog.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

/**
 * Public catalog predicates. Only ACTIVE products inside ACTIVE categories are
 * ever visible; variant predicates always constrain to active variants.
 */
final class ProductSpecifications {

	private ProductSpecifications() {
	}

	static Specification<ProductEntity> publicCatalog(ProductQuery query, String categoryId) {
		return (root, criteriaQuery, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("status"), ProductEntity.Status.ACTIVE));
			predicates.add(inActiveCategory(criteriaQuery, cb, root));

			if (StringUtils.hasText(categoryId)) {
				predicates.add(cb.equal(root.get("categoryId"), categoryId));
			}

			if (StringUtils.hasText(query.getQ())) {
				String pattern = containsPattern(query.getQ());
				predicates.add(cb.or(
					cb.like(cb.lower(root.get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("shortDescription")), pattern, '\\'),
					cb.like(cb.lower(root.get("description")), pattern, '\\')
				));
			}

			if (query.getMinPrice() != null) {
				predicates.add(cb.greaterThanOrEqualTo(
					root.get("basePrice"), query.getMinPrice()));
			}
			if (query.getMaxPrice() != null) {
				predicates.add(cb.lessThanOrEqualTo(
					root.get("basePrice"), query.getMaxPrice()));
			}

			if (StringUtils.hasText(query.getVariantSize())) {
				String size = normalise(query.getVariantSize());
				predicates.add(cb.exists(variantSubquery(criteriaQuery, cb, root,
					(variant, vcb) -> vcb.equal(vcb.lower(variant.get("size")), size))));
			}

			if (StringUtils.hasText(query.getColor())) {
				String color = normalise(query.getColor());
				predicates.add(cb.exists(variantSubquery(criteriaQuery, cb, root,
					(variant, vcb) -> vcb.equal(vcb.lower(variant.get("color")), color))));
			}

			if (query.getInStock() != null) {
				Subquery<String> hasStock = variantSubquery(criteriaQuery, cb, root,
					(variant, vcb) -> vcb.greaterThan(variant.get("stockQuantity"), 0));
				predicates.add(query.getInStock()
					? cb.exists(hasStock)
					: cb.not(cb.exists(hasStock)));
			}

			if (query.getOnSale() != null) {
				Subquery<String> discounted = variantSubquery(criteriaQuery, cb, root,
					(variant, vcb) -> vcb.lessThan(
						variant.get("priceOverride"), root.get("basePrice")));
				predicates.add(query.getOnSale()
					? cb.exists(discounted)
					: cb.not(cb.exists(discounted)));
			}

			if (query.getFeatured() != null) {
				predicates.add(cb.equal(root.get("featured"), query.getFeatured()));
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static Predicate inActiveCategory(
		CriteriaQuery<?> criteriaQuery, CriteriaBuilder cb, Root<ProductEntity> root) {
		Subquery<String> subquery = criteriaQuery.subquery(String.class);
		Root<CategoryEntity> category = subquery.from(CategoryEntity.class);
		subquery.select(category.get("id")).where(
			cb.isTrue(category.get("isActive")),
			cb.equal(category.get("id"), root.get("categoryId"))
		);
		return cb.exists(subquery);
	}

	/**
	 * Builds an EXISTS subquery over active variants with one extra predicate on
	 * the same variant root. The caller composes this with {@code cb.exists(...)}.
	 */
	private static Subquery<String> variantSubquery(
		CriteriaQuery<?> criteriaQuery,
		CriteriaBuilder cb,
		Root<ProductEntity> root,
		VariantPredicate factory) {
		Subquery<String> subquery = criteriaQuery.subquery(String.class);
		Root<ProductVariantEntity> variant = subquery.from(ProductVariantEntity.class);
		Predicate variantPredicate = factory.test(variant, cb);
		subquery.select(variant.get("id")).where(
			cb.equal(variant.get("productId"), root.get("id")),
			cb.isTrue(variant.get("isActive")),
			variantPredicate
		);
		return subquery;
	}

	private static String normalise(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static String containsPattern(String value) {
		String escaped = normalise(value)
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

	@FunctionalInterface
	private interface VariantPredicate {
		Predicate test(Root<ProductVariantEntity> variant, CriteriaBuilder cb);
	}
}

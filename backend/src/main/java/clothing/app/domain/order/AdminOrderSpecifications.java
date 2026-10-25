package clothing.app.domain.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Admin order filters (DESIGN_REFERENCE §5.3, Phase 6: "searches and filters
 * orders"). Every predicate is optional so the same spec serves the unfiltered
 * list and any combination of filters.
 */
public final class AdminOrderSpecifications {

	private AdminOrderSpecifications() {
	}

	public static Specification<OrderEntity> matching(String status, String q, Instant placedFrom,
			Instant placedBefore) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (StringUtils.hasText(status)) {
				predicates.add(builder.equal(root.get("status"),
					OrderEntity.Status.valueOf(status.trim().toUpperCase(Locale.ROOT))));
			}
			if (StringUtils.hasText(q)) {
				// Order number is the handle support has; the customer's email is
				// not stored on the order, so it cannot be searched here.
				String needle = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
				predicates.add(builder.like(builder.lower(root.get("orderNumber")), needle));
			}
			if (placedFrom != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("placedAt"), placedFrom));
			}
			if (placedBefore != null) {
				predicates.add(builder.lessThan(root.get("placedAt"), placedBefore));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	/**
	 * Same filters plus "order belongs to one of these customers", used for the
	 * email search. An empty id list matches nothing, which is the correct
	 * answer for an email search that found no account.
	 */
	public static Specification<OrderEntity> matchingCustomers(String status, List<String> userIds,
			Instant placedFrom, Instant placedBefore) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (StringUtils.hasText(status)) {
				predicates.add(builder.equal(root.get("status"),
					OrderEntity.Status.valueOf(status.trim().toUpperCase(Locale.ROOT))));
			}
			predicates.add(root.get("userId").in(userIds));
			if (placedFrom != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("placedAt"), placedFrom));
			}
			if (placedBefore != null) {
				predicates.add(builder.lessThan(root.get("placedAt"), placedBefore));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}

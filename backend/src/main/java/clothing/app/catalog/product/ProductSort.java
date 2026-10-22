package clothing.app.catalog.product;

import org.springframework.data.domain.Sort;

/**
 * Allowlisted catalog sorts. No user-supplied property name reaches the query.
 *
 * <p>{@code popularity} has no trustworthy order metric yet (orders arrive in a
 * later phase), so it deterministically falls back to featured first, then newest.
 */
public enum ProductSort {

	NEWEST("newest", Sort.by(
		Sort.Order.desc("createdAt"),
		Sort.Order.asc("id"))),

	PRICE_ASC("price_asc", Sort.by(
		Sort.Order.asc("basePrice"),
		Sort.Order.asc("id"))),

	PRICE_DESC("price_desc", Sort.by(
		Sort.Order.desc("basePrice"),
		Sort.Order.asc("id"))),

	NAME_ASC("name_asc", Sort.by(
		Sort.Order.asc("name"),
		Sort.Order.asc("id"))),

	POPULARITY("popularity", Sort.by(
		Sort.Order.desc("featured"),
		Sort.Order.desc("createdAt"),
		Sort.Order.asc("id")));

	private final String key;
	private final Sort sort;

	ProductSort(String key, Sort sort) {
		this.key = key;
		this.sort = sort;
	}

	public String getKey() {
		return key;
	}

	public Sort getSort() {
		return sort;
	}

	public static ProductSort fromKey(String key) {
		for (ProductSort value : values()) {
			if (value.key.equals(key)) {
				return value;
			}
		}
		throw new IllegalArgumentException("Unsupported sort: " + key);
	}
}

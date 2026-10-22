package clothing.app.catalog.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Public catalog query parameters.
 *
 * <p>{@code size} is the pagination page size (1–48), matching the API-wide
 * pagination contract. The apparel size filter is exposed as {@code variantSize}
 * because the design excerpt lists {@code size} for both purposes and the two
 * cannot share one parameter.
 */
public class ProductQuery {

	@Size(max = 100, message = "Search text must be at most 100 characters")
	private String q;

	@Size(max = 200, message = "Category slug must be at most 200 characters")
	private String category;

	@PositiveOrZero(message = "minPrice must be zero or greater")
	private Long minPrice;

	@PositiveOrZero(message = "maxPrice must be zero or greater")
	private Long maxPrice;

	@Size(max = 32, message = "Size filter must be at most 32 characters")
	private String variantSize;

	@Size(max = 64, message = "Color filter must be at most 64 characters")
	private String color;

	private Boolean inStock;

	private Boolean onSale;

	private Boolean featured;

	@Pattern(
		regexp = "newest|price_asc|price_desc|name_asc|popularity",
		message = "sort must be one of newest, price_asc, price_desc, name_asc, popularity"
	)
	private String sort = "newest";

	@Min(value = 0, message = "page must be zero or greater")
	private int page = 0;

	@Min(value = 1, message = "size must be between 1 and 48")
	@Max(value = 48, message = "size must be between 1 and 48")
	private int size = 20;

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Long getMinPrice() {
		return minPrice;
	}

	public void setMinPrice(Long minPrice) {
		this.minPrice = minPrice;
	}

	public Long getMaxPrice() {
		return maxPrice;
	}

	public void setMaxPrice(Long maxPrice) {
		this.maxPrice = maxPrice;
	}

	public String getVariantSize() {
		return variantSize;
	}

	public void setVariantSize(String variantSize) {
		this.variantSize = variantSize;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public Boolean getInStock() {
		return inStock;
	}

	public void setInStock(Boolean inStock) {
		this.inStock = inStock;
	}

	public Boolean getOnSale() {
		return onSale;
	}

	public void setOnSale(Boolean onSale) {
		this.onSale = onSale;
	}

	public Boolean getFeatured() {
		return featured;
	}

	public void setFeatured(Boolean featured) {
		this.featured = featured;
	}

	public String getSort() {
		return sort;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
}

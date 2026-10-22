package clothing.app.domain.catalog.product;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "product_variants")
public class ProductVariantEntity extends BaseEntity {

	@Column(name = "product_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String productId;

	@Column(name = "sku", length = 64, nullable = false, unique = true)
	private String sku;

	@Column(name = "size", length = 32)
	private String size;

	@Column(name = "color", length = 64)
	private String color;

	@Column(name = "price_override")
	private Long priceOverride;

	@Column(name = "stock_quantity", nullable = false)
	private Integer stockQuantity;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public Long getPriceOverride() {
		return priceOverride;
	}

	public void setPriceOverride(Long priceOverride) {
		this.priceOverride = priceOverride;
	}

	public Integer getStockQuantity() {
		return stockQuantity;
	}

	public void setStockQuantity(Integer stockQuantity) {
		this.stockQuantity = stockQuantity;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}
}

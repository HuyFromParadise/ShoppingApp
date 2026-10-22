package clothing.app.domain.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "order_items")
public class OrderItemEntity extends BaseEntity {

	@Column(name = "order_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String orderId;

	@Column(name = "variant_id", length = 36, columnDefinition = "char(36)")
	private String variantId;

	@Column(name = "product_name_snapshot", length = 200, nullable = false)
	private String productNameSnapshot;

	@Column(name = "sku_snapshot", length = 64)
	private String skuSnapshot;

	@Column(name = "size_snapshot", length = 32)
	private String sizeSnapshot;

	@Column(name = "color_snapshot", length = 64)
	private String colorSnapshot;

	@Column(name = "image_url_snapshot", length = 500)
	private String imageUrlSnapshot;

	@Column(name = "unit_price", nullable = false)
	private Long unitPrice;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "line_total", nullable = false)
	private Long lineTotal;

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public String getVariantId() {
		return variantId;
	}

	public void setVariantId(String variantId) {
		this.variantId = variantId;
	}

	public String getProductNameSnapshot() {
		return productNameSnapshot;
	}

	public void setProductNameSnapshot(String productNameSnapshot) {
		this.productNameSnapshot = productNameSnapshot;
	}

	public String getSkuSnapshot() {
		return skuSnapshot;
	}

	public void setSkuSnapshot(String skuSnapshot) {
		this.skuSnapshot = skuSnapshot;
	}

	public String getSizeSnapshot() {
		return sizeSnapshot;
	}

	public void setSizeSnapshot(String sizeSnapshot) {
		this.sizeSnapshot = sizeSnapshot;
	}

	public String getColorSnapshot() {
		return colorSnapshot;
	}

	public void setColorSnapshot(String colorSnapshot) {
		this.colorSnapshot = colorSnapshot;
	}

	public String getImageUrlSnapshot() {
		return imageUrlSnapshot;
	}

	public void setImageUrlSnapshot(String imageUrlSnapshot) {
		this.imageUrlSnapshot = imageUrlSnapshot;
	}

	public Long getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(Long unitPrice) {
		this.unitPrice = unitPrice;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public Long getLineTotal() {
		return lineTotal;
	}

	public void setLineTotal(Long lineTotal) {
		this.lineTotal = lineTotal;
	}
}

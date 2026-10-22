package clothing.app.domain.cart;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "cart_items")
public class CartItemEntity extends BaseEntity {

	@Column(name = "cart_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String cartId;

	@Column(name = "variant_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String variantId;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	public String getCartId() {
		return cartId;
	}

	public void setCartId(String cartId) {
		this.cartId = cartId;
	}

	public String getVariantId() {
		return variantId;
	}

	public void setVariantId(String variantId) {
		this.variantId = variantId;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}
}

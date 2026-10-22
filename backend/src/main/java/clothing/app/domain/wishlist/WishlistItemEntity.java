package clothing.app.domain.wishlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "wishlist_items")
public class WishlistItemEntity extends BaseEntity {

	@Column(name = "wishlist_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String wishlistId;

	@Column(name = "variant_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String variantId;

	public String getWishlistId() {
		return wishlistId;
	}

	public void setWishlistId(String wishlistId) {
		this.wishlistId = wishlistId;
	}

	public String getVariantId() {
		return variantId;
	}

	public void setVariantId(String variantId) {
		this.variantId = variantId;
	}
}

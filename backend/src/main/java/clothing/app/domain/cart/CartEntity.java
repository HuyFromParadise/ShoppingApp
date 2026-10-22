package clothing.app.domain.cart;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "carts")
public class CartEntity extends BaseEntity {

	@Column(name = "user_id", length = 36, columnDefinition = "char(36)", nullable = false, unique = true)
	private String userId;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}
}

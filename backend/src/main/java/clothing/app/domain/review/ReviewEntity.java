package clothing.app.domain.review;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "reviews")
public class ReviewEntity extends BaseEntity {

	@Column(name = "product_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String productId;

	@Column(name = "user_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String userId;

	@Column(name = "rating", nullable = false)
	private Integer rating;

	@Column(name = "title", length = 200)
	private String title;

	@Column(name = "comment", columnDefinition = "text")
	private String comment;

	@Column(name = "is_verified", nullable = false)
	private Boolean isVerified = Boolean.FALSE;

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Integer getRating() {
		return rating;
	}

	public void setRating(Integer rating) {
		this.rating = rating;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public Boolean getIsVerified() {
		return isVerified;
	}

	public void setIsVerified(Boolean isVerified) {
		this.isVerified = isVerified;
	}
}

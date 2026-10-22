package clothing.app.domain.promotion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "promotions")
public class PromotionEntity extends BaseEntity {

	@Column(name = "code", length = 50, nullable = false, unique = true)
	private String code;

	@Column(name = "name", length = 200)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "type", nullable = false)
	@Enumerated(EnumType.STRING)
	private Type type;

	@Column(name = "value", nullable = false)
	private Long value;

	@Column(name = "min_order", nullable = false)
	private Long minOrder = 0L;

	@Column(name = "max_discount")
	private Long maxDiscount;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public Long getValue() {
		return value;
	}

	public void setValue(Long value) {
		this.value = value;
	}

	public Long getMinOrder() {
		return minOrder;
	}

	public void setMinOrder(Long minOrder) {
		this.minOrder = minOrder;
	}

	public Long getMaxDiscount() {
		return maxDiscount;
	}

	public void setMaxDiscount(Long maxDiscount) {
		this.maxDiscount = maxDiscount;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public void setStartsAt(Instant startsAt) {
		this.startsAt = startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public void setEndsAt(Instant endsAt) {
		this.endsAt = endsAt;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public enum Type {
		PERCENT, FIXED
	}
}

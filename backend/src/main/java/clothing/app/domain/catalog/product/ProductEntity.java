package clothing.app.domain.catalog.product;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "products")
public class ProductEntity extends BaseEntity {

	@Column(name = "name", length = 200, nullable = false)
	private String name;

	@Column(name = "slug", length = 200, nullable = false, unique = true)
	private String slug;

	@Column(name = "short_description", length = 500)
	private String shortDescription;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "category_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String categoryId;

	@Column(name = "base_price", nullable = false)
	private Long basePrice;

	@Column(name = "currency", length = 3, columnDefinition = "char(3)", nullable = false)
	private String currency = "VND";

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "material", length = 200)
	private String material;

	@Column(name = "fit", length = 100)
	private String fit;

	@Column(name = "care_instructions", columnDefinition = "text")
	private String careInstructions;

	@Column(name = "featured", nullable = false)
	private Boolean featured = Boolean.FALSE;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		this.shortDescription = shortDescription;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(String categoryId) {
		this.categoryId = categoryId;
	}

	public Long getBasePrice() {
		return basePrice;
	}

	public void setBasePrice(Long basePrice) {
		this.basePrice = basePrice;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getMaterial() {
		return material;
	}

	public void setMaterial(String material) {
		this.material = material;
	}

	public String getFit() {
		return fit;
	}

	public void setFit(String fit) {
		this.fit = fit;
	}

	public String getCareInstructions() {
		return careInstructions;
	}

	public void setCareInstructions(String careInstructions) {
		this.careInstructions = careInstructions;
	}

	public Boolean getFeatured() {
		return featured;
	}

	public void setFeatured(Boolean featured) {
		this.featured = featured;
	}

	public enum Status {
		DRAFT, ACTIVE, ARCHIVED
	}
}

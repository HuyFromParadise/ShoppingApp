package clothing.app.domain.catalog.category;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "categories")
public class CategoryEntity extends BaseEntity {

	@Column(name = "name", length = 200, nullable = false)
	private String name;

	@Column(name = "slug", length = 200, nullable = false, unique = true)
	private String slug;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "parent_id", length = 36, columnDefinition = "char(36)")
	private String parentId;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;

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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}
}

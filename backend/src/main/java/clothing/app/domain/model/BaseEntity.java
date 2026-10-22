package clothing.app.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for all JPA entities.
 *
 * Conventions (per DESIGN_REFERENCE §4 / ADR-006):
 * - Primary key is CHAR(36) UUID, generated in the application via {@link UUID#randomUUID()}.
 * - Every table has created_at / updated_at (UTC, DATETIME(6)) managed by Spring Data auditing.
 * - Money is stored as BIGINT minor units (Long) in subclasses.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@jakarta.persistence.Id
	@Column(columnDefinition = "char(36)", nullable = false, updatable = false)
	private String id;

	@CreatedDate
	@Column(columnDefinition = "datetime(6)", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(columnDefinition = "datetime(6)", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onPrePersist() {
		if (id == null) {
			id = UUID.randomUUID().toString();
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof BaseEntity))
			return false;
		return id != null && id.equals(((BaseEntity) o).id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}

package clothing.app.domain.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity extends BaseEntity {

	@Column(name = "actor_user_id", length = 36, columnDefinition = "char(36)")
	private String actorUserId;

	@Column(name = "action", length = 100, nullable = false)
	private String action;

	@Column(name = "target_type", length = 100)
	private String targetType;

	@Column(name = "target_id", length = 36, columnDefinition = "char(36)")
	private String targetId;

	@Column(name = "metadata", columnDefinition = "json")
	private String metadata;

	public String getActorUserId() {
		return actorUserId;
	}

	public void setActorUserId(String actorUserId) {
		this.actorUserId = actorUserId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getTargetType() {
		return targetType;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

	public String getTargetId() {
		return targetId;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}
}

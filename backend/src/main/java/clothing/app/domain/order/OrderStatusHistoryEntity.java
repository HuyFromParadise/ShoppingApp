package clothing.app.domain.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity extends BaseEntity {

	@Column(name = "order_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String orderId;

	@Column(name = "from_status")
	@Enumerated(EnumType.STRING)
	private OrderEntity.Status fromStatus;

	@Column(name = "to_status", nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderEntity.Status toStatus;

	@Column(name = "actor_user_id", length = 36, columnDefinition = "char(36)")
	private String actorUserId;

	@Column(name = "note", length = 500)
	private String note;

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public OrderEntity.Status getFromStatus() {
		return fromStatus;
	}

	public void setFromStatus(OrderEntity.Status fromStatus) {
		this.fromStatus = fromStatus;
	}

	public OrderEntity.Status getToStatus() {
		return toStatus;
	}

	public void setToStatus(OrderEntity.Status toStatus) {
		this.toStatus = toStatus;
	}

	public String getActorUserId() {
		return actorUserId;
	}

	public void setActorUserId(String actorUserId) {
		this.actorUserId = actorUserId;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}

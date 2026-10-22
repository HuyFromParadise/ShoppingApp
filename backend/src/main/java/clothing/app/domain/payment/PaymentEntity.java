package clothing.app.domain.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "payments")
public class PaymentEntity extends BaseEntity {

	@Column(name = "order_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String orderId;

	@Column(name = "provider", length = 32, nullable = false)
	private String provider;

	@Column(name = "provider_transaction_id", length = 120)
	private String providerTransactionId;

	@Column(name = "amount")
	private Long amount;

	@Column(name = "currency", length = 3, columnDefinition = "char(3)")
	private String currency;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getProviderTransactionId() {
		return providerTransactionId;
	}

	public void setProviderTransactionId(String providerTransactionId) {
		this.providerTransactionId = providerTransactionId;
	}

	public Long getAmount() {
		return amount;
	}

	public void setAmount(Long amount) {
		this.amount = amount;
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

	public enum Status {
		PENDING, AUTHORIZED, PAID, FAILED, CANCELLED, REFUNDED
	}
}

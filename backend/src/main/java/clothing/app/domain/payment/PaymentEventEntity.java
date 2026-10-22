package clothing.app.domain.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "payment_events")
public class PaymentEventEntity extends BaseEntity {

	@Column(name = "provider", length = 32, nullable = false)
	private String provider;

	@Column(name = "provider_event_id", length = 120, nullable = false)
	private String providerEventId;

	@Column(name = "payload_sha256", length = 64, columnDefinition = "char(64)", nullable = false)
	private String payloadSha256;

	@Column(name = "processed_at")
	private Instant processedAt;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getProviderEventId() {
		return providerEventId;
	}

	public void setProviderEventId(String providerEventId) {
		this.providerEventId = providerEventId;
	}

	public String getPayloadSha256() {
		return payloadSha256;
	}

	public void setPayloadSha256(String payloadSha256) {
		this.payloadSha256 = payloadSha256;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(Instant processedAt) {
		this.processedAt = processedAt;
	}
}

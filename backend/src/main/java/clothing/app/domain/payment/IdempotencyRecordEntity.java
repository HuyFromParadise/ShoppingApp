package clothing.app.domain.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import clothing.app.domain.model.BaseEntity;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity extends BaseEntity {

	@Column(name = "user_id", length = 36, columnDefinition = "char(36)", nullable = false)
	private String userId;

	@Column(name = "idempotency_key", length = 80, nullable = false)
	private String idempotencyKey;

	@Column(name = "request_hash", length = 64, columnDefinition = "char(64)", nullable = false)
	private String requestHash;

	@Column(name = "status_code", nullable = false)
	private Integer statusCode;

	@Column(name = "response_body", columnDefinition = "mediumtext")
	private String responseBody;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public void setRequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	public Integer getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(Integer statusCode) {
		this.statusCode = statusCode;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}
}

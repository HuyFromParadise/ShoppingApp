package clothing.app.domain.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

/**
 * Records admin-sensitive changes in {@code audit_events} (PROJECT_SPEC §10
 * AuditEvent). Audit rows are written inside the caller's transaction, so a
 * failed change leaves no audit trail of something that never happened.
 *
 * <p>Only identifiers and value transitions are stored. No password, token,
 * payment secret or customer personal data is ever passed in here
 * (PROJECT_SPEC §10: "Do not log ... unnecessary personal data").
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AuditService {

	private final AuditEventRepository auditEventRepository;
	private final ObjectMapper objectMapper;

	public AuditService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
		this.auditEventRepository = auditEventRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * Records one action.
	 *
	 * @param action   stable, dotted name such as {@code variant.stock.set}
	 * @param metadata safe key/value pairs (from/to values, counts); may be empty
	 */
	public void record(String actorUserId, String action, String targetType, String targetId,
			Map<String, ?> metadata) {
		AuditEventEntity event = new AuditEventEntity();
		event.setActorUserId(actorUserId);
		event.setAction(action);
		event.setTargetType(targetType);
		event.setTargetId(targetId);
		event.setMetadata(serialize(metadata));
		auditEventRepository.save(event);
	}

	/** Convenience for a change of two values, e.g. stock 5 to 8. */
	public void recordChange(String actorUserId, String action, String targetType,
			String targetId, String field, Object from, Object to) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("field", field);
		metadata.put("from", from);
		metadata.put("to", to);
		record(actorUserId, action, targetType, targetId, metadata);
	}

	private String serialize(Map<String, ?> metadata) {
		if (metadata == null || metadata.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(metadata);
		}
		catch (JsonProcessingException ex) {
			// An unreadable audit payload must not block the change it describes,
			// but it must be visible in the log.
			throw new IllegalStateException("Could not serialize audit metadata", ex);
		}
	}
}

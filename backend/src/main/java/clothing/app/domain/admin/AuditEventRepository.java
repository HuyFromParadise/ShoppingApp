package clothing.app.domain.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

	List<AuditEventEntity> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType,
		String targetId);
}

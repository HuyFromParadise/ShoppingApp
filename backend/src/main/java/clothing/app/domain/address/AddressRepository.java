package clothing.app.domain.address;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<AddressEntity, String> {

	List<AddressEntity> findByUserIdOrderByIsDefaultDescCreatedAtDesc(String userId);

	List<AddressEntity> findByUserIdAndIsDefaultTrue(String userId);

	Optional<AddressEntity> findByIdAndUserId(String id, String userId);
}

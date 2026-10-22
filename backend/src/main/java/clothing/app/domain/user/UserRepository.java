package clothing.app.domain.user;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, String> {

	Optional<UserEntity> findByEmail(String email);

	boolean existsByEmail(String email);

	/**
	 * Locks the user row until the end of the transaction. The cart uses it as
	 * the per-customer mutex, so two parallel requests cannot create two carts
	 * or overwrite each other's cart line.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from UserEntity u where u.id = :id")
	Optional<UserEntity> findByIdForUpdate(@Param("id") String id);
}

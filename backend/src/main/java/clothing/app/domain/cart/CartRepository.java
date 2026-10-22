package clothing.app.domain.cart;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<CartEntity, String> {

	Optional<CartEntity> findByUserId(String userId);
}

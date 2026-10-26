package clothing.app.domain.wishlist;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistEntity, String> {

	Optional<WishlistEntity> findByUserId(String userId);
}

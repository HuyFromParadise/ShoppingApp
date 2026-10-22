package clothing.app.domain.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CartItemRepository extends JpaRepository<CartItemEntity, String> {

	List<CartItemEntity> findByCartIdOrderByCreatedAtAsc(String cartId);

	Optional<CartItemEntity> findByCartIdAndVariantId(String cartId, String variantId);

	Optional<CartItemEntity> findByIdAndCartId(String id, String cartId);

	@Transactional
	void deleteByCartId(String cartId);
}

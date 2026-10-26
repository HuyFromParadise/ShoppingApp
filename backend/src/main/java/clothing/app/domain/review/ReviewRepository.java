package clothing.app.domain.review;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<ReviewEntity, String> {

	List<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId);

	boolean existsByProductIdAndUserId(String productId, String userId);

	Optional<ReviewEntity> findByProductIdAndUserId(String productId, String userId);

	/** Averaging is done in the database so a large product never loads its reviews. */
	@org.springframework.data.jpa.repository.Query(
		"select count(r), coalesce(avg(r.rating), 0) from ReviewEntity r where r.productId = :productId")
	Object[] summary(@org.springframework.data.repository.query.Param("productId") String productId);
}

package clothing.app.domain.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Webhook dedupe: {@code UNIQUE (provider, provider_event_id)} (DESIGN_REFERENCE §4). */
public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, String> {

	boolean existsByProviderAndProviderEventId(String provider, String providerEventId);

	Optional<PaymentEventEntity> findByProviderAndProviderEventId(String provider,
		String providerEventId);
}

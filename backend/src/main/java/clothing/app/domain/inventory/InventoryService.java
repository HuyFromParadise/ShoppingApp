package clothing.app.domain.inventory;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;

/**
 * The one place stock is written (ADR-005, DESIGN_REFERENCE §8).
 *
 * <p>Stock is deducted at order creation, never reserved: the conditional
 * update in {@link ProductVariantRepository#decrementStock} both checks and
 * writes, so concurrent checkouts cannot take the same unit. Release (restock)
 * belongs to cancellation and is added with the order workflow.
 */
@Service
@Transactional
public class InventoryService {

	private final ProductVariantRepository variantRepository;

	public InventoryService(ProductVariantRepository variantRepository) {
		this.variantRepository = variantRepository;
	}

	/** Decrements stock or fails the whole checkout transaction. */
	public void decrement(String variantId, int quantity) {
		if (quantity < 1) {
			throw new IllegalArgumentException("Quantity to decrement must be positive");
		}
		int updated = variantRepository.decrementStock(variantId, quantity, Instant.now());
		if (updated != 1) {
			throw new ApiException.OutOfStock("Not enough stock to complete the order");
		}
	}
}

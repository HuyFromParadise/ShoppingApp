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
 * writes, so concurrent checkouts cannot take the same unit. Release is the
 * single cancellation path: because the stock was gone before payment, a
 * cancelled order must put it back.
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
		requirePositive(quantity);
		int updated = variantRepository.decrementStock(variantId, quantity, Instant.now());
		if (updated != 1) {
			throw new ApiException.OutOfStock("Not enough stock to complete the order");
		}
	}

	/**
	 * Puts stock back for a released order. The caller must already have moved
	 * the order out of a holding status in this transaction, which is what keeps
	 * the release idempotent (DESIGN_REFERENCE §7).
	 */
	public void release(String variantId, int quantity) {
		requirePositive(quantity);
		if (variantId == null) {
			// A deleted variant (ON DELETE SET NULL) cannot take stock back.
			return;
		}
		variantRepository.incrementStock(variantId, quantity, Instant.now());
	}

	private static void requirePositive(int quantity) {
		if (quantity < 1) {
			throw new IllegalArgumentException("Stock movement quantity must be positive");
		}
	}
}

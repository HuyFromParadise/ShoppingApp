package clothing.app.domain.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.order.OrderEntity;

/**
 * Dashboard counts and revenue (DESIGN_REFERENCE §5.3, Phase 6).
 *
 * <p><b>What counts as revenue.</b> Money is only counted for orders whose
 * payment actually settled: {@code PAID}. An order cancelled after payment is
 * {@code REFUNDED} (step 13) and therefore excluded, so the figure never
 * includes money that was given back. Unpaid and pending orders are reported
 * separately rather than mixed into revenue.
 *
 * <p>Two windows are returned: the current rolling 30 days and all time, plus
 * the order counts per status. Low stock uses one threshold from configuration
 * because there is no per-product threshold in the schema.
 */
@Service
public class AdminDashboardService {

	static final int LOW_STOCK_THRESHOLD = 5;
	private static final Duration RECENT_WINDOW = Duration.ofDays(30);

	private final EntityManager entityManager;

	public AdminDashboardService(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public AdminDashboardSummary summary() {
		Instant now = Instant.now();
		Instant since = now.minus(RECENT_WINDOW);

		Map<String, Long> ordersByStatus = new LinkedHashMap<>();
		for (Object[] row : rows("select o.status, count(o) from OrderEntity o group by o.status")) {
			ordersByStatus.put(((Enum<?>) row[0]).name(), (Long) row[1]);
		}

		long activeProducts = count(
			"select count(p) from ProductEntity p where p.status = :status",
			ProductEntity.Status.ACTIVE);
		long draftProducts = count(
			"select count(p) from ProductEntity p where p.status = :status",
			ProductEntity.Status.DRAFT);
		long archivedProducts = count(
			"select count(p) from ProductEntity p where p.status = :status",
			ProductEntity.Status.ARCHIVED);

		return new AdminDashboardSummary(
			ordersByStatus,
			revenue(since),
			revenue(Instant.EPOCH),
			activeProducts,
			draftProducts,
			archivedProducts,
			lowStockVariants(LOW_STOCK_THRESHOLD),
			LOW_STOCK_THRESHOLD,
			now);
	}

	@Transactional(readOnly = true)
	public List<LowStockVariant> lowStockVariants(int threshold) {
		return entityManager.createQuery(
				"select v.id, v.sku, v.size, v.color, v.stockQuantity, p.name, p.id "
					+ "from ProductVariantEntity v, ProductEntity p "
					+ "where p.id = v.productId and v.isActive = true "
					+ "and v.stockQuantity <= :threshold order by v.stockQuantity asc, p.name asc",
				Object[].class)
			.setParameter("threshold", threshold)
			.setMaxResults(50)
			.getResultList()
			.stream()
			.map(row -> new LowStockVariant((String) row[0], (String) row[1], (String) row[2],
				(String) row[3], ((Number) row[4]).intValue(), (String) row[5], (String) row[6]))
			.toList();
	}

	private RevenueWindow revenue(Instant since) {
		Object[] totals = entityManager.createQuery(
				"select count(o), coalesce(sum(o.grandTotal), 0) from OrderEntity o "
					+ "where o.paymentStatus = :paid and o.placedAt >= :since",
				Object[].class)
			.setParameter("paid", OrderEntity.PaymentStatus.PAID)
			.setParameter("since", since)
			.getSingleResult();
		Object[] tickets = entityManager.createQuery(
				"select count(o), coalesce(sum(o.grandTotal), 0) from OrderEntity o "
					+ "where o.placedAt >= :since",
				Object[].class)
			.setParameter("since", since)
			.getSingleResult();
		long paidOrders = ((Number) totals[0]).longValue();
		long revenue = ((Number) totals[1]).longValue();
		return new RevenueWindow(since, paidOrders, revenue,
			paidOrders == 0 ? 0 : revenue / paidOrders, ((Number) tickets[0]).longValue());
	}

	private long count(String jpql, Object parameter) {
		return ((Number) entityManager.createQuery(jpql)
			.setParameter("status", parameter)
			.getSingleResult()).longValue();
	}

	private List<Object[]> rows(String jpql) {
		return entityManager.createQuery(jpql, Object[].class).getResultList();
	}

	/** One period's settled money; amounts are minor units (ADR-008). */
	public record RevenueWindow(
			Instant since,
			long paidOrders,
			long revenueMinor,
			long averageOrderValueMinor,
			long ordersPlaced) {
	}

	public record LowStockVariant(
			String variantId,
			String sku,
			String size,
			String color,
			int stockQuantity,
			String productName,
			String productId) {
	}

	public record AdminDashboardSummary(
			Map<String, Long> ordersByStatus,
			RevenueWindow revenueLast30Days,
			RevenueWindow revenueAllTime,
			long activeProducts,
			long draftProducts,
			long archivedProducts,
			List<LowStockVariant> lowStock,
			int lowStockThreshold,
			Instant generatedAt) {
	}
}

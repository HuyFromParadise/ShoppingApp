package clothing.app.domain.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway baseline migration V1__baseline.sql defines every table
 * from DESIGN_REFERENCE §4, without requiring a live MySQL connection.
 */
class BaselineMigrationTest {

	@Test
	void baselineMigrationDefinesAllExpectedTables(@TempDir Path tempDir) {
		Path migration = Path.of("src/main/resources/db/migration/V1__baseline.sql");
		assertThat(migration).exists();

		String sql;
		try {
			sql = Files.readString(migration);
		} catch (java.io.IOException e) {
			throw new AssertionError("Unable to read baseline migration", e);
		}

		assertThat(sql).contains("ENGINE=InnoDB");
		assertThat(sql).contains("utf8mb4_0900_ai_ci");
		assertThat(sql).contains("CHAR(36)");
		assertThat(sql).contains("DATETIME(6)");
		assertThat(sql).contains("BIGINT");

		List<String> tables = List.of(
			"users", "refresh_tokens", "addresses",
			"categories", "products", "product_images", "product_variants",
			"carts", "cart_items",
			"orders", "order_items", "order_status_history",
			"payments", "payment_events", "idempotency_records",
			"wishlists", "wishlist_items", "promotions", "reviews", "audit_events"
		);

		for (String table : tables) {
			assertThat(sql).contains("CREATE TABLE " + table + " (")
				.as("baseline migration should define table %s", table);
		}
	}
}

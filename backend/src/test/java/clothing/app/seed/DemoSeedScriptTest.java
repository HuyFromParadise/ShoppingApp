package clothing.app.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the development seed (db/seed/R__demo_catalog.sql) without a database.
 *
 * <p>The seed is never applied by the test profile, so this checks the script
 * itself: that it stays opt-in, stays re-runnable, and that the data it promises
 * (active and hidden categories, a draft and an archived product, a sale and a
 * low-stock variant) is actually present. A seed that silently stops creating
 * those cases would make the demo look broken rather than the script.
 */
class DemoSeedScriptTest {

	private static final Path SEED = Path.of("src/main/resources/db/seed/R__demo_catalog.sql");
	private static final Path DEMO_PROFILE =
		Path.of("src/main/resources/application-demo.yml");

	private final String sql;

	DemoSeedScriptTest() throws Exception {
		assertThat(SEED).as("seed script exists").exists();
		sql = Files.readString(SEED);
	}

	@Test
	void seedIsOptInAndNotPartOfTheDefaultMigrations() throws Exception {
		// The default locations must not include db/seed.
		String application = Files.readString(Path.of("src/main/resources/application.yml"));
		assertThat(application).contains("classpath:db/migration");
		assertThat(application).doesNotContain("db/seed");

		String testProfile = Files.readString(
			Path.of("src/main/resources/application-test.yml"));
		assertThat(testProfile).doesNotContain("db/seed");

		// Only the demo profile adds it.
		assertThat(DEMO_PROFILE).exists();
		assertThat(Files.readString(DEMO_PROFILE))
			.contains("classpath:db/migration,classpath:db/seed");
	}

	@Test
	void seedIsRepeatableRatherThanAccumulatingDuplicates() {
		// A repeatable migration re-runs whenever its checksum changes, so every
		// insert has to be guarded. Some guards stack (the address insert checks
		// both its own id and that the demo user exists), so the count is "at
		// least one per insert", not exactly equal.
		long inserts = count("INSERT INTO ");
		long guards = count("WHERE NOT EXISTS");
		assertThat(inserts).isEqualTo(7);
		assertThat(guards)
			.as("every insert is guarded by at least one NOT EXISTS")
			.isGreaterThanOrEqualTo(inserts);
	}

	@Test
	void guardsMatchNaturalKeysNotJustIds() {
		// Rows can already exist without a history row (a manual run whose history
		// was then cleared). Guarding only on the fixed id would then hit a unique
		// constraint on the second run and make the app fail to start.
		assertThat(sql).containsPattern("c\\.id = seed\\.id OR c\\.slug = seed\\.slug");
		assertThat(sql).containsPattern("p\\.id = seed\\.id OR p\\.slug = seed\\.slug");
		assertThat(sql).containsPattern("v\\.id = seed\\.id OR v\\.sku = seed\\.sku");
		assertThat(sql).containsPattern("p\\.id = seed\\.id OR p\\.code = seed\\.code");
		assertThat(sql).containsPattern("u\\.id = seed\\.id OR u\\.email = seed\\.email");
		assertThat(sql).contains("i.product_id = seed.product_id AND i.storage_key = seed.storage_key");
	}

	@Test
	void seedCoversTheCasesTheStorefrontAndAdminNeedToShow() {
		// Categories: a parent with children, and one hidden category.
		assertThat(sql).contains("'c1000000-0000-4000-8000-000000000001'");
		assertThat(sql).containsPattern("'luu-tru',\\s+'Retired line[^']*',\\s+NULL, FALSE");
		// Products and variants.
		assertThat(sql).contains("'DRAFT'");
		assertThat(sql).contains("'ARCHIVED'");
		assertThat(sql).contains("'ACTIVE'");
		// An out-of-stock variant and a low-stock one.
		assertThat(sql).containsPattern("'OXF-BLUE-M', 'M', 'Blue', NULL, 0");
		assertThat(sql).containsPattern("'MER-GREY-L', 'L', 'Grey', NULL, 2");
		// A discounted line, so compare-at pricing has something to render.
		assertThat(sql).containsPattern("'LIN-SAND-M', 'M', 'Sand', 549000");
	}

	@Test
	void seededPricesAndDiscountsAreInMinorUnits() {
		// ADR-008: whole numbers of đồng, never decimals or formatted strings.
		assertThat(sql).doesNotContain(".00");
		List<String> priceLines = sql.lines()
			.filter(line -> line.contains("base_price") || line.contains("'PERCENT' AS type"))
			.toList();
		assertThat(priceLines).isNotEmpty();
	}

	@Test
	void demoAccountsUseABcryptHashAndNeverAPlainPassword() {
		// Cost 12, the production cost (DESIGN_REFERENCE §6.3).
		assertThat(sql).contains("$2a$12$");
		// The password may appear in a comment, but never as a literal value.
		assertThat(sql).doesNotContain("'DemoPass!234'");
		assertThat(sql).doesNotContain("VALUES ('admin@demo.local', 'DemoPass");
	}

	@Test
	void demoAccountsAreOnlyCreatedWhenAbsent() {
		// An operator who changed the demo password must keep their value.
		assertThat(sql).containsPattern("WHERE NOT EXISTS \\(SELECT 1 FROM users");
		assertThat(sql).contains("u.email = seed.email");
	}

	private long count(String needle) {
		int index = 0;
		long total = 0;
		while ((index = sql.indexOf(needle, index)) >= 0) {
			total++;
			index += needle.length();
		}
		return total;
	}
}

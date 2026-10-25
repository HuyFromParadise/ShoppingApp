package clothing.app.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Slug derivation shared by products and categories. */
class AdminSlugTest {

	@Test
	void lowercasesAndHyphenates() {
		assertThat(AdminProductService.toSlug("Oxford Shirt")).isEqualTo("oxford-shirt");
		assertThat(AdminProductService.toSlug("  Wide   Leg Trousers ")).isEqualTo("wide-leg-trousers");
	}

	@Test
	void stripsAccentsSoVietnameseNamesStayUrlSafe() {
		assertThat(AdminProductService.toSlug("Áo Sơ Mi Trắng")).isEqualTo("ao-so-mi-trang");
		assertThat(AdminProductService.toSlug("Đầm Dạ Hội")).isEqualTo("dam-da-hoi");
	}

	@Test
	void collapsesPunctuationAndTrimsEdgeHyphens() {
		assertThat(AdminProductService.toSlug("T-Shirt (Slim Fit)")).isEqualTo("t-shirt-slim-fit");
		assertThat(AdminProductService.toSlug("--Jeans--")).isEqualTo("jeans");
		assertThat(AdminProductService.toSlug("100% Cotton!")).isEqualTo("100-cotton");
	}

	@Test
	void fallsBackToARandomSlugWhenNothingUsableRemains() {
		// CJK only: transliteration is not attempted, so toSlug is empty and
		// slugify supplies a generated one. A product must never get an empty slug.
		assertThat(AdminProductService.toSlug("連衣裙")).isEmpty();
		assertThat(AdminProductService.toSlug("")).isEmpty();
		assertThat(AdminProductService.toSlug(null)).isEmpty();

		String slug = AdminProductService.slugify("連衣裙");
		assertThat(slug).startsWith("item-");
		assertThat(AdminProductService.slugify("連衣裙")).isNotEqualTo(slug);
	}

	@Test
	void keepsAnOrdinaryNameAsIs() {
		assertThat(AdminProductService.slugify("Oxford Shirt")).isEqualTo("oxford-shirt");
	}
}

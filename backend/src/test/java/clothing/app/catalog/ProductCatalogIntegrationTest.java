package clothing.app.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.catalog.category.CategoryRepository;
import clothing.app.catalog.product.ProductImageRepository;
import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.domain.catalog.category.CategoryEntity;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductImageEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public catalog contract against the real MySQL test schema (ADR-004):
 * visibility rules, filters, sorting, pagination, detail shape and validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductCatalogIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductVariantRepository variantRepository;

	@Autowired
	private ProductImageRepository imageRepository;

	private CategoryEntity tops;
	private CategoryEntity bottoms;

	@BeforeEach
	void cleanCatalog() {
		imageRepository.deleteAll();
		variantRepository.deleteAll();
		productRepository.deleteAll();
		categoryRepository.deleteAll();

		tops = categoryRepository.save(category("Tops", "tops", true, 1));
		bottoms = categoryRepository.save(category("Bottoms", "bottoms", true, 2));
	}

	@Test
	void listsOnlyActiveProductsInActiveCategories() throws Exception {
		ProductEntity visible = productRepository.save(
			product("Linen Shirt", "linen-shirt", tops, 499000, ProductEntity.Status.ACTIVE, true));
		variantRepository.save(variant(visible, "LS-M-WHITE", "M", "White", null, 4, true));

		productRepository.save(
			product("Draft Shirt", "draft-shirt", tops, 100000, ProductEntity.Status.DRAFT, false));
		productRepository.save(
			product("Archived Shirt", "archived-shirt", tops, 100000, ProductEntity.Status.ARCHIVED, false));

		CategoryEntity hidden = categoryRepository.save(category("Hidden", "hidden", false, 3));
		productRepository.save(
			product("Hidden Shirt", "hidden-shirt", hidden, 100000, ProductEntity.Status.ACTIVE, false));

		mockMvc.perform(get("/api/v1/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("linen-shirt"))
			.andExpect(jsonPath("$.data[0].category.slug").value("tops"))
			.andExpect(jsonPath("$.data[0].priceMinor").value(499000))
			.andExpect(jsonPath("$.data[0].inStock").value(true))
			.andExpect(jsonPath("$.data[0].onSale").value(false));
	}

	@Test
	void filtersBySearchCategoryPriceAndVariantAttributes() throws Exception {
		ProductEntity shirt = productRepository.save(
			product("Linen Shirt", "linen-shirt", tops, 500000, ProductEntity.Status.ACTIVE, false));
		variantRepository.save(variant(shirt, "LS-M-WHITE", "M", "White", 400000L, 3, true));

		ProductEntity trousers = productRepository.save(
			product("Wool Trousers", "wool-trousers", bottoms, 900000, ProductEntity.Status.ACTIVE, false));
		variantRepository.save(variant(trousers, "WT-M-BLACK", "M", "Black", null, 0, true));

		ProductEntity cotton = productRepository.save(
			product("Cotton Shirt", "cotton-shirt", tops, 300000, ProductEntity.Status.ACTIVE, false));
		variantRepository.save(variant(cotton, "CS-S-BLUE", "S", "Blue", null, 5, true));

		mockMvc.perform(get("/api/v1/products").param("q", "shirt"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/products")
				.param("q", "shirt")
				.param("category", "tops")
				.param("minPrice", "350000")
				.param("maxPrice", "600000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("linen-shirt"));

		mockMvc.perform(get("/api/v1/products")
				.param("variantSize", "m")
				.param("color", "WHITE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("linen-shirt"));

		mockMvc.perform(get("/api/v1/products").param("inStock", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/products").param("onSale", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("linen-shirt"))
			.andExpect(jsonPath("$.data[0].priceMinor").value(400000))
			.andExpect(jsonPath("$.data[0].compareAtPriceMinor").value(500000))
			.andExpect(jsonPath("$.data[0].onSale").value(true));
	}

	@Test
	void sortsAndPaginatesWithAllowlistedKeys() throws Exception {
		productRepository.save(product("Beta Jacket", "beta-jacket", tops, 300000, ProductEntity.Status.ACTIVE, false));
		productRepository.save(product("Alpha Coat", "alpha-coat", tops, 100000, ProductEntity.Status.ACTIVE, false));
		productRepository.save(product("Gamma Vest", "gamma-vest", tops, 200000, ProductEntity.Status.ACTIVE, false));

		mockMvc.perform(get("/api/v1/products")
				.param("sort", "price_asc")
				.param("size", "2")
				.param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].slug").value("alpha-coat"))
			.andExpect(jsonPath("$.data[1].slug").value("gamma-vest"));

		mockMvc.perform(get("/api/v1/products")
				.param("sort", "price_asc")
				.param("size", "2")
				.param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("beta-jacket"));

		mockMvc.perform(get("/api/v1/products").param("sort", "name_asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].slug").value("alpha-coat"))
			.andExpect(jsonPath("$.data[1].slug").value("beta-jacket"));

		mockMvc.perform(get("/api/v1/products").param("sort", "price_desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].slug").value("beta-jacket"));
	}

	@Test
	void returnsProductDetailWithImagesAndActiveVariantsOnly() throws Exception {
		ProductEntity product = productRepository.save(
			product("Linen Shirt", "linen-shirt", tops, 500000, ProductEntity.Status.ACTIVE, true));
		imageRepository.save(image(product, "images/secondary.jpg", 2, false));
		imageRepository.save(image(product, "images/primary.jpg", 1, true));

		variantRepository.save(variant(product, "LS-M-WHITE", "M", "White", 400000L, 2, true));
		variantRepository.save(variant(product, "LS-L-WHITE", "L", "White", null, 0, true));
		variantRepository.save(variant(product, "LS-S-WHITE", "S", "White", 100000L, 9, false));

		mockMvc.perform(get("/api/v1/products/linen-shirt"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.slug").value("linen-shirt"))
			.andExpect(jsonPath("$.data.category.slug").value("tops"))
			.andExpect(jsonPath("$.data.basePriceMinor").value(500000))
			.andExpect(jsonPath("$.data.priceMinor").value(400000))
			.andExpect(jsonPath("$.data.compareAtPriceMinor").value(500000))
			.andExpect(jsonPath("$.data.onSale").value(true))
			.andExpect(jsonPath("$.data.inStock").value(true))
			.andExpect(jsonPath("$.data.images.length()").value(2))
			.andExpect(jsonPath("$.data.images[0].url").value("images/primary.jpg"))
			.andExpect(jsonPath("$.data.images[0].primary").value(true))
			.andExpect(jsonPath("$.data.variants.length()").value(2))
			.andExpect(jsonPath("$.data.variants[?(@.sku == 'LS-S-WHITE')]").isEmpty());
	}

	@Test
	void hidesDraftArchivedAndInactiveCategoryProducts() throws Exception {
		productRepository.save(
			product("Draft Shirt", "draft-shirt", tops, 100000, ProductEntity.Status.DRAFT, false));
		productRepository.save(
			product("Archived Shirt", "archived-shirt", tops, 100000, ProductEntity.Status.ARCHIVED, false));

		CategoryEntity hidden = categoryRepository.save(category("Hidden", "hidden", false, 9));
		productRepository.save(
			product("Hidden Shirt", "hidden-shirt", hidden, 100000, ProductEntity.Status.ACTIVE, false));

		mockMvc.perform(get("/api/v1/products/draft-shirt"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/v1/products/archived-shirt"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/products/hidden-shirt"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/products/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Product not found"));
	}

	@Test
	void listsActiveCategoriesAndCategoryProducts() throws Exception {
		categoryRepository.save(category("Sale", "sale", true, 0));
		CategoryEntity inactive = categoryRepository.save(category("Inactive", "inactive", false, 5));
		ProductEntity product = productRepository.save(
			product("Linen Shirt", "linen-shirt", tops, 500000, ProductEntity.Status.ACTIVE, false));
		variantRepository.save(variant(product, "LS-M-WHITE", "M", "White", null, 1, true));

		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].slug").value("sale"))
			.andExpect(jsonPath("$.data[1].slug").value("tops"))
			.andExpect(jsonPath("$.data[2].slug").value("bottoms"));

		mockMvc.perform(get("/api/v1/categories/tops/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("linen-shirt"));

		mockMvc.perform(get("/api/v1/categories/inactive/products"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/v1/categories/missing/products"))
			.andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidQueryParameters() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.page").exists());

		mockMvc.perform(get("/api/v1/products").param("size", "49"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.size").exists());

		mockMvc.perform(get("/api/v1/products").param("sort", "recent"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.sort").exists());

		mockMvc.perform(get("/api/v1/products").param("inStock", "maybe"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.inStock").exists());

		mockMvc.perform(get("/api/v1/products")
				.param("minPrice", "900000")
				.param("maxPrice", "100000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.minPrice").exists());
	}

	@Test
	void returnsEmptyPageForUnknownCategoryFilter() throws Exception {
		productRepository.save(
			product("Linen Shirt", "linen-shirt", tops, 500000, ProductEntity.Status.ACTIVE, false));

		mockMvc.perform(get("/api/v1/products").param("category", "missing"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	private static CategoryEntity category(String name, String slug, boolean active, int sortOrder) {
		CategoryEntity category = new CategoryEntity();
		category.setName(name);
		category.setSlug(slug);
		category.setDescription(name + " description");
		category.setIsActive(active);
		category.setSortOrder(sortOrder);
		return category;
	}

	private static ProductEntity product(
		String name,
		String slug,
		CategoryEntity category,
		long basePrice,
		ProductEntity.Status status,
		boolean featured) {
		ProductEntity product = new ProductEntity();
		product.setName(name);
		product.setSlug(slug);
		product.setShortDescription(name + " short description");
		product.setDescription(name + " full description");
		product.setCategoryId(category.getId());
		product.setBasePrice(basePrice);
		product.setCurrency("VND");
		product.setStatus(status);
		product.setFeatured(featured);
		return product;
	}

	private static ProductVariantEntity variant(
		ProductEntity product,
		String sku,
		String size,
		String color,
		Long priceOverride,
		int stock,
		boolean active) {
		ProductVariantEntity variant = new ProductVariantEntity();
		variant.setProductId(product.getId());
		variant.setSku(sku);
		variant.setSize(size);
		variant.setColor(color);
		variant.setPriceOverride(priceOverride);
		variant.setStockQuantity(stock);
		variant.setIsActive(active);
		return variant;
	}

	private static ProductImageEntity image(
		ProductEntity product, String storageKey, int sortOrder, boolean primary) {
		ProductImageEntity image = new ProductImageEntity();
		image.setProductId(product.getId());
		image.setStorageKey(storageKey);
		image.setAltText(product.getName());
		image.setSortOrder(sortOrder);
		image.setIsPrimary(primary);
		return image;
	}
}

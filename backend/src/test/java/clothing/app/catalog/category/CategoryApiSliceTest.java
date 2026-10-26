package clothing.app.catalog.category;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import clothing.app.catalog.category.dto.CategoryResponse;
import clothing.app.catalog.category.dto.CategorySummaryResponse;
import clothing.app.catalog.product.ProductService;
import clothing.app.catalog.product.dto.ProductSummaryResponse;
import clothing.app.common.exception.ApiException;
import clothing.app.common.exception.GlobalExceptionHandler;
import clothing.app.common.response.PageResponse;
import clothing.app.common.security.CurrentUserArgumentResolver;
import clothing.app.common.security.JwtAuthenticationFilter;
import clothing.app.common.security.JwtService;
import clothing.app.common.security.SecurityConfig;
import clothing.app.common.security.SecurityErrorHandlers;
import clothing.app.domain.user.UserRepository;

/**
 * API slice for the public category endpoints: the active-only list, the
 * category product delegate, and the 404 envelope for a hidden or missing slug.
 */
@WebMvcTest(controllers = CategoryController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorHandlers.class,
	CurrentUserArgumentResolver.class})
class CategoryApiSliceTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CategoryService categoryService;

	@MockBean
	private ProductService productService;

	@MockBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockBean
	private JwtService jwtService;

	@MockBean
	private UserRepository userRepository;

	@BeforeEach
	void jwtFilterPassesThrough() throws Exception {
		org.mockito.Mockito.doAnswer(invocation -> {
			jakarta.servlet.ServletRequest request = invocation.getArgument(0);
			jakarta.servlet.ServletResponse response = invocation.getArgument(1);
			jakarta.servlet.FilterChain chain = invocation.getArgument(2);
			chain.doFilter(request, response);
			return null;
		}).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
	}

	@Test
	void listsActiveCategoriesInTheSuccessEnvelope() throws Exception {
		when(categoryService.listActive()).thenReturn(List.of(
			new CategoryResponse("c1", "Shirts", "shirts", "Shirts and tops", null, 1)));

		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("shirts"));
	}

	@Test
	void delegatesCategoryProductsToTheCatalogQuery() throws Exception {
		CategorySummaryResponse shirts = new CategorySummaryResponse("c1", "Shirts", "shirts");
		when(productService.listForCategory(any(), any())).thenReturn(PageResponse.of(
			List.of(new ProductSummaryResponse("p1", "Oxford Shirt", "oxford-shirt", null,
				shirts, 499000L, null, "VND", false, true, false, null, null)),
			0, 20, 1));

		mockMvc.perform(get("/api/v1/categories/shirts/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.data[0].category.slug").value("shirts"));
	}

	@Test
	void returnsNotFoundForAnUnknownOrHiddenCategory() throws Exception {
		when(productService.listForCategory(any(), any()))
			.thenThrow(new ApiException.NotFound("Category not found"));

		mockMvc.perform(get("/api/v1/categories/hidden/products"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Category not found"));
	}

	@Test
	void rejectsAnInvalidPageSize() throws Exception {
		mockMvc.perform(get("/api/v1/categories/shirts/products?size=0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors").exists());
	}
}

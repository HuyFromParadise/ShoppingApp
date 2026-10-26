package clothing.app.catalog.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import clothing.app.catalog.category.dto.CategorySummaryResponse;
import clothing.app.catalog.product.dto.ProductDetailResponse;
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
 * API slice for the public product endpoints (DESIGN_REFERENCE §10 "Backend
 * slice"): the envelope, the validation messages and the status codes, with the
 * service mocked so only the web layer is under test.
 */
@WebMvcTest(controllers = ProductController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorHandlers.class,
	CurrentUserArgumentResolver.class})
class ProductApiSliceTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private ProductService productService;

	@MockBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockBean
	private JwtService jwtService;

	@MockBean
	private UserRepository userRepository;

	private static final CategorySummaryResponse CATEGORY =
		new CategorySummaryResponse("c1", "Shirts", "shirts");

	@BeforeEach
	void jwtFilterPassesThrough() throws Exception {
		// The real filter needs a database; the slice only checks routing and shape.
		org.mockito.Mockito.doAnswer(invocation -> {
			jakarta.servlet.ServletRequest request = invocation.getArgument(0);
			jakarta.servlet.ServletResponse response = invocation.getArgument(1);
			jakarta.servlet.FilterChain chain = invocation.getArgument(2);
			chain.doFilter(request, response);
			return null;
		}).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
	}

	@Test
	void listsProductsInThePaginatedEnvelope() throws Exception {
		when(productService.list(any())).thenReturn(PageResponse.of(
			List.of(new ProductSummaryResponse("p1", "Oxford Shirt", "oxford-shirt",
				"A shirt", CATEGORY, 499000L, null, "VND", false, true, false, null, null)),
			0, 20, 1));

		mockMvc.perform(get("/api/v1/products"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.data[0].slug").value("oxford-shirt"));
	}

	@Test
	void returnsAProductDetailInTheSuccessEnvelope() throws Exception {
		when(productService.getBySlug("oxford-shirt")).thenReturn(new ProductDetailResponse(
			"p1", "Oxford Shirt", "oxford-shirt", "A shirt", "Long", CATEGORY, 499000L, 499000L,
			null, "VND", false, true, false, "Cotton", "Regular", null, List.of(), List.of(),
			null, null));

		mockMvc.perform(get("/api/v1/products/oxford-shirt"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Success"))
			.andExpect(jsonPath("$.data.name").value("Oxford Shirt"))
			.andExpect(jsonPath("$.data.category.slug").value("shirts"));
	}

	@Test
	void rejectsBadQueryParametersWithTheFieldErrorEnvelope() throws Exception {
		// The controller's @ModelAttribute validation runs before the service.
		mockMvc.perform(get("/api/v1/products?size=99"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors").exists())
			.andExpect(jsonPath("$.path").value("/api/v1/products"));
	}

	@Test
	void rendersNotFoundFromTheServiceAsTheErrorEnvelope() throws Exception {
		when(productService.getBySlug("missing"))
			.thenThrow(new ApiException.NotFound("Product not found"));

		mockMvc.perform(get("/api/v1/products/missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Product not found"))
			.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void catalogReadsStayPublic() throws Exception {
		when(productService.list(any())).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
		when(productService.getBySlug("oxford-shirt")).thenReturn(new ProductDetailResponse(
			"p1", "Oxford Shirt", "oxford-shirt", null, null, CATEGORY, 499000L, 499000L,
			null, "VND", false, true, false, null, null, null, List.of(), List.of(),
			null, null));

		// No Authorization header: the security chain must permit both reads.
		mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/products/oxford-shirt")).andExpect(status().isOk());
	}
}

package clothing.app.domain.checkout;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import clothing.app.common.exception.ApiException;

/**
 * Configurable shipping options (PROJECT_SPEC §14: start with a simple
 * server-side strategy; no carrier API exists yet).
 *
 * <p>Defaults match {@code app.checkout.shipping} in {@code application.yml};
 * the YAML is the place to change fees without touching code.
 */
@Component
@ConfigurationProperties(prefix = "app.checkout.shipping")
public class ShippingStrategy {

	private List<Method> methods = List.of(
		new Method("STANDARD", "Standard delivery", 30000L),
		new Method("EXPRESS", "Express delivery", 60000L));

	/** Resolves a request code (case-insensitive) or fails with a field error. */
	public Method resolve(String code) {
		String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
		for (Method method : methods) {
			if (method.code().equalsIgnoreCase(normalized)) {
				return method;
			}
		}
		throw new ApiException.Validation("Choose a supported shipping method",
			Map.of("shippingMethod", "Must be one of " + String.join(", ",
				methods.stream().map(Method::code).toList())));
	}

	public List<Method> getMethods() {
		return methods;
	}

	public void setMethods(List<Method> methods) {
		this.methods = methods == null ? List.of() : List.copyOf(methods);
	}

	/** A shipping option and the fee snapshot stored on the order. */
	public record Method(String code, String name, long feeMinor) {
	}
}

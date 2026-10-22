package clothing.app.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the local .env file is wired into Spring Boot's configuration
 * (spring.config.import) and that every placeholder used by the YAML config
 * is covered by .env.example.
 */
class EnvImportTest {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)");

	@Test
	void envFileIsImportedAsAPropertiesSource(@TempDir Path tempDir) throws Exception {
		Path envFile = tempDir.resolve(".env");
		Files.writeString(envFile, String.join("\n",
			"CLOTHING_TEST_KEY=hello-from-env",
			"CLOTHING_TEST_URL=jdbc:mysql://localhost:3306/x?useSSL=false&serverTimezone=UTC",
			"# a comment line",
			"CLOTHING_TEST_EMPTY=",
			""));

		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("import-test",
			Map.of("spring.config.import", "optional:file:" + envFile.toString() + "[.properties]")));

		ConfigDataEnvironmentPostProcessor.applyTo(environment);

		assertThat(environment.getProperty("CLOTHING_TEST_KEY")).isEqualTo("hello-from-env");
		assertThat(environment.getProperty("CLOTHING_TEST_URL"))
			.isEqualTo("jdbc:mysql://localhost:3306/x?useSSL=false&serverTimezone=UTC");
		assertThat(environment.getProperty("CLOTHING_TEST_EMPTY")).isEmpty();
	}

	@Test
	void applicationYamlDeclaresTheEnvImport() throws Exception {
		String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

		assertThat(yaml).contains("import:");
		assertThat(yaml).contains("optional:file:.env[.properties]");
		assertThat(yaml).contains("optional:file:backend/.env[.properties]");
	}

	@Test
	void envProfileIsMappedToSpringProfilesActive() throws Exception {
		String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

		// SPRING_PROFILES_ACTIVE inside .env only works when mapped explicitly,
		// because the active profile is resolved before imported config is applied.
		assertThat(yaml).contains("active: ${SPRING_PROFILES_ACTIVE:");
	}

	@Test
	void everyYamlPlaceholderIsCoveredByEnvExample() throws Exception {
		List<String> configFiles = List.of(
			"src/main/resources/application.yml",
			"src/main/resources/application-test.yml");

		Set<String> placeholders = new LinkedHashSet<>();
		for (String file : configFiles) {
			Matcher matcher = PLACEHOLDER.matcher(Files.readString(Path.of(file)));
			while (matcher.find()) {
				placeholders.add(matcher.group(1));
			}
		}

		Properties example = new Properties();
		try (var reader = Files.newBufferedReader(Path.of(".env.example"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				int eq = trimmed.indexOf('=');
				if (eq > 0) {
					example.setProperty(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
				}
			}
		}

		assertThat(placeholders).isNotEmpty();
		for (String placeholder : placeholders) {
			assertThat(example.containsKey(placeholder))
				.as(".env.example must define %s (used by the YAML config)", placeholder)
				.isTrue();
		}
	}
}

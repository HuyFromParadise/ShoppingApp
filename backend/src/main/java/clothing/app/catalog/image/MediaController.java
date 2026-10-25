package clothing.app.catalog.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;

/**
 * Serves stored product images read-only at {@code /media/products/{file}}.
 *
 * <p>Only a bare file name is accepted, so no {@code ..} path segment can escape
 * the storage directory. Content is immutable (names are random), so it is
 * cacheable for a long time.
 */
@Controller
@RequestMapping("/media/products")
public class MediaController {

	private final LocalImageStorage storage;

	public MediaController(LocalImageStorage storage) {
		this.storage = storage;
	}

	@GetMapping("/{fileName:.+}")
	public ResponseEntity<Resource> image(@PathVariable String fileName) throws IOException {
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			return ResponseEntity.notFound().build();
		}
		Path file = storage.root().resolve(fileName).normalize();
		if (!file.startsWith(storage.root()) || !Files.isRegularFile(file)) {
			return ResponseEntity.notFound().build();
		}
		ImageStorage.DetectedImageType type = ImageStorage.DetectedImageType
			.ofExtension(extensionOf(fileName));
		if (type == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(type.contentType()))
			.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
			.body(new UrlResource(file.toUri()));
	}

	private static String extensionOf(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot);
	}
}

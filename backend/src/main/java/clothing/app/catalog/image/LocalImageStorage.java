package clothing.app.catalog.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk image storage (ADR-003: no external service in local dev).
 *
 * <p>Defence in depth for uploads (DESIGN_REFERENCE §6.3):
 * <ul>
 *   <li>the type is decided by magic bytes, never by the declared content type
 *       or the filename;</li>
 *   <li>the extension is derived from the detected type;</li>
 *   <li>the stored filename is a random UUID, so a client name can never place
 *       a file, or a {@code ../} traversal, on disk;</li>
 *   <li>the size cap is enforced before and during the copy.</li>
 * </ul>
 *
 * <p>Files live under {@code app.media.root} and are served read-only at
 * {@code /media/**}.
 */
@Component
public class LocalImageStorage implements ImageStorage {

	private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);

	static final String PUBLIC_PREFIX = "/media/products/";
	private static final String PRODUCTS_DIR = "products";

	private final Path root;

	public LocalImageStorage(@Value("${app.media.root:uploads}") String root) {
		this.root = Paths.get(root).toAbsolutePath().normalize().resolve(PRODUCTS_DIR);
		try {
			Files.createDirectories(this.root);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not create the image directory " + this.root,
				ex);
		}
	}

	@Override
	public StoredImage store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ImageRejectedException("Choose an image file");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ImageRejectedException("Images must be 5 MB or smaller");
		}

		DetectedImageType type;
		try (InputStream input = file.getInputStream()) {
			type = ImageStorage.sniff(input);
		}
		catch (IOException ex) {
			throw new ImageRejectedException("The image could not be read");
		}

		String fileName = UUID.randomUUID().toString().replace("-", "") + type.extension();
		Path target = root.resolve(fileName).normalize();
		if (!target.startsWith(root)) {
			throw new ImageRejectedException("Invalid image path");
		}

		try (InputStream input = file.getInputStream()) {
			long copied = Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
			if (copied > MAX_BYTES) {
				Files.deleteIfExists(target);
				throw new ImageRejectedException("Images must be 5 MB or smaller");
			}
			return new StoredImage(PUBLIC_PREFIX + fileName, type.contentType(), copied);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not store the image", ex);
		}
	}

	@Override
	public void delete(String publicPath) {
		if (publicPath == null || !publicPath.startsWith(PUBLIC_PREFIX)) {
			return;
		}
		String fileName = publicPath.substring(PUBLIC_PREFIX.length());
		// Only a bare file name is ours; anything with a separator is not.
		if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
			return;
		}
		Path target = root.resolve(fileName).normalize();
		if (!target.startsWith(root)) {
			return;
		}
		try {
			Files.deleteIfExists(target);
		}
		catch (IOException ex) {
			// An orphaned file is harmless; log it so a cleanup job can find it.
			log.warn("Could not delete image {}", publicPath, ex);
		}
	}

	/** The directory files are served from; used to wire the read-only handler. */
	public Path root() {
		return root;
	}
}

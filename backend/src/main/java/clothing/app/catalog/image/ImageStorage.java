package clothing.app.catalog.image;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stores product images behind a strategy so the local-disk implementation can
 * be replaced by an object store without touching the admin API
 * (PROJECT_SPEC §1.2, DESIGN_REFERENCE §3).
 */
public interface ImageStorage {

	/** Hard cap from DESIGN_REFERENCE §6.3. */
	long MAX_BYTES = 5L * 1024 * 1024;

	/**
	 * Validates and stores one upload.
	 *
	 * @return the public path of the stored image, e.g. {@code /media/products/….jpg}
	 * @throws ImageRejectedException when the file is empty, too large or not a
	 *     real JPEG/PNG/WebP (checked by magic bytes, not by the client's claim)
	 */
	StoredImage store(MultipartFile file);

	/** Deletes a previously stored image; a missing file is not an error. */
	void delete(String publicPath);

	/** A stored image and what it really is. */
	record StoredImage(String publicPath, String contentType, long sizeBytes) {
	}

	/** A client error about one uploaded file. */
	class ImageRejectedException extends RuntimeException {

		public ImageRejectedException(String message) {
			super(message);
		}
	}

	/**
	 * The image type is decided from the file's leading bytes. The declared
	 * content type and the filename are attacker-controlled, so neither is used.
	 */
	static DetectedImageType sniff(InputStream input) throws IOException {
		byte[] header = input.readNBytes(16);
		if (header.length >= 3
				&& (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
				&& (header[2] & 0xFF) == 0xFF) {
			return DetectedImageType.JPEG;
		}
		if (header.length >= 8
				&& (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N'
				&& header[3] == 'G' && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
				&& (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
			return DetectedImageType.PNG;
		}
		if (header.length >= 12
				&& header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
				&& header[8] == 'W' && header[9] == 'E' && header[10] == 'B'
				&& header[11] == 'P') {
			return DetectedImageType.WEBP;
		}
		throw new ImageRejectedException("Only JPEG, PNG or WebP images are accepted");
	}

	/** The accepted types, with the extension derived on the server side. */
	enum DetectedImageType {
		JPEG(".jpg", "image/jpeg"),
		PNG(".png", "image/png"),
		WEBP(".webp", "image/webp");

		private final String extension;
		private final String contentType;

		DetectedImageType(String extension, String contentType) {
			this.extension = extension;
			this.contentType = contentType;
		}

		public String extension() {
			return extension;
		}

		public String contentType() {
			return contentType;
		}

		/** Accept header value used when serving files from disk. */
		public static DetectedImageType ofExtension(String extension) {
			for (DetectedImageType type : values()) {
				if (type.extension.equalsIgnoreCase(extension)) {
					return type;
				}
			}
			return null;
		}
	}
}

package clothing.app.catalog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import clothing.app.catalog.image.ImageStorage.DetectedImageType;
import clothing.app.catalog.image.ImageStorage.ImageRejectedException;

/**
 * Upload validation (DESIGN_REFERENCE §6.3): the type comes from the bytes, and
 * anything that is not a real JPEG/PNG/WebP is refused regardless of what the
 * client claims it is.
 */
class ImageSniffingTest {

	@Test
	void recognisesJpegPngAndWebp() {
		byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
		byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
		byte[] webp = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

		assertThat(sniff(jpeg)).isEqualTo(DetectedImageType.JPEG);
		assertThat(sniff(png)).isEqualTo(DetectedImageType.PNG);
		assertThat(sniff(webp)).isEqualTo(DetectedImageType.WEBP);
	}

	@Test
	void extensionsAndContentTypesAreDerivedOnTheServer() {
		assertThat(DetectedImageType.JPEG.extension()).isEqualTo(".jpg");
		assertThat(DetectedImageType.JPEG.contentType()).isEqualTo("image/jpeg");
		assertThat(DetectedImageType.PNG.extension()).isEqualTo(".png");
		assertThat(DetectedImageType.WEBP.contentType()).isEqualTo("image/webp");
		assertThat(DetectedImageType.ofExtension(".PNG")).isEqualTo(DetectedImageType.PNG);
		assertThat(DetectedImageType.ofExtension(".svg")).isNull();
	}

	@Test
	void refusesAScriptPretendingToBeAnImage() {
		// The classic polyglot: an HTML/JS payload named .jpg, declared image/jpeg.
		byte[] script = "<script>alert(1)</script>".getBytes();
		assertThatThrownBy(() -> sniff(script))
			.isInstanceOf(ImageRejectedException.class)
			.hasMessageContaining("JPEG, PNG or WebP");
	}

	@Test
	void refusesAnSvgBecauseItCanCarryScript() {
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes();
		assertThatThrownBy(() -> sniff(svg)).isInstanceOf(ImageRejectedException.class);
	}

	@Test
	void refusesATruncatedOrEmptyFile() {
		assertThatThrownBy(() -> sniff(new byte[0])).isInstanceOf(ImageRejectedException.class);
		assertThatThrownBy(() -> sniff(new byte[] {(byte) 0xFF}))
			.isInstanceOf(ImageRejectedException.class);
	}

	private static DetectedImageType sniff(byte[] bytes) {
		try {
			return ImageStorage.sniff(new ByteArrayInputStream(bytes));
		}
		catch (Exception ex) {
			throw new ImageRejectedException(ex.getMessage() == null ? "rejected"
				: ex.getMessage());
		}
	}
}

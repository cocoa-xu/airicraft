package ai.moeru.airicraft;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LetterboxImageScalerTest {
	@Test
	void scalesLandscapeSourceWithoutCropping() {
		BufferedImage source = filledImage(1920, 1080, Color.RED);

		BufferedImage scaled = LetterboxImageScaler.scaleToCanvas(source, 854, 480);

		assertEquals(854, scaled.getWidth());
		assertEquals(480, scaled.getHeight());
		assertEquals(Color.RED.getRGB(), scaled.getRGB(427, 240));
		assertEquals(Color.RED.getRGB(), scaled.getRGB(0, 240));
		assertEquals(Color.BLACK.getRGB(), scaled.getRGB(853, 240));
	}

	@Test
	void addsSideBarsForTallSource() {
		BufferedImage source = filledImage(600, 900, Color.BLUE);

		BufferedImage scaled = LetterboxImageScaler.scaleToCanvas(source, 854, 480);

		assertEquals(Color.BLACK.getRGB(), scaled.getRGB(0, 240));
		assertEquals(Color.BLUE.getRGB(), scaled.getRGB(427, 240));
		assertEquals(Color.BLACK.getRGB(), scaled.getRGB(853, 240));
	}

	@Test
	void addsTopAndBottomBarsForWideSource() {
		BufferedImage source = filledImage(1200, 300, Color.GREEN);

		BufferedImage scaled = LetterboxImageScaler.scaleToCanvas(source, 854, 480);

		assertEquals(Color.BLACK.getRGB(), scaled.getRGB(427, 0));
		assertEquals(Color.GREEN.getRGB(), scaled.getRGB(427, 240));
		assertEquals(Color.BLACK.getRGB(), scaled.getRGB(427, 479));
	}

	private static BufferedImage filledImage(int width, int height, Color color) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				image.setRGB(x, y, color.getRGB());
			}
		}
		return image;
	}
}

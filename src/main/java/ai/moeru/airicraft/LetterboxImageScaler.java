package ai.moeru.airicraft;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class LetterboxImageScaler {
	private LetterboxImageScaler() {
	}

	public static BufferedImage scaleToCanvas(BufferedImage source, int targetWidth, int targetHeight) {
		if (source == null) {
			throw new IllegalArgumentException("source must not be null");
		}
		if (source.getWidth() <= 0 || source.getHeight() <= 0) {
			throw new IllegalArgumentException("source image must have positive dimensions");
		}
		if (targetWidth <= 0 || targetHeight <= 0) {
			throw new IllegalArgumentException("target dimensions must be positive");
		}

		double scale = Math.min(
			(double) targetWidth / source.getWidth(),
			(double) targetHeight / source.getHeight()
		);
		int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
		int offsetX = (targetWidth - scaledWidth) / 2;
		int offsetY = (targetHeight - scaledHeight) / 2;

		BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try {
			graphics.setColor(Color.BLACK);
			graphics.fillRect(0, 0, targetWidth, targetHeight);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, offsetX, offsetY, scaledWidth, scaledHeight, null);
		}
		finally {
			graphics.dispose();
		}

		return canvas;
	}
}

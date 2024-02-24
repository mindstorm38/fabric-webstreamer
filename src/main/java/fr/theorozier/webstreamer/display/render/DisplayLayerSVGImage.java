package fr.theorozier.webstreamer.display.render;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.geometry.size.FloatSize;
import com.github.weisj.jsvg.parser.SVGLoader;
import org.apache.commons.io.IOUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class DisplayLayerSVGImage extends DisplayLayerImage {
	private final SVGOptions options;

	public DisplayLayerSVGImage(SVGOptions options, URI uri, DisplayLayerResources res) {
		super(uri, res);
		this.options = options;
	}

	@Override
	protected STBLoadedImage readImageBlocking(InputStream stream) throws IOException {
		SVGLoader loader = new SVGLoader();
		SVGDocument svgDocument = loader.load(stream);
		ByteBuffer buf = null;
		try {
			if (svgDocument == null) {
				throw new IOException("Could not load image: SVG Image is null");
			}

			// Calculate scaling factors based on block's configured width and height
			float blockResolution = 512; // block resolution in pixels
			int blockWidth = (int) (options.width * blockResolution); // Convert blocks to pixels
			int blockHeight = (int) (options.height * blockResolution); // Convert blocks to pixels
			FloatSize svgSize = svgDocument.size();
			float scaleX = blockWidth / svgSize.width;
			float scaleY = blockHeight / svgSize.height;

			// Convert SVG to Buffered Image
			BufferedImage image = new BufferedImage(blockWidth, blockHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.scale(scaleX, scaleY); // Apply scaling
			svgDocument.render(null, g);
			g.dispose();

			// Convert BufferedImage to byte array
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			ImageIO.write(image, "png", byteArrayOutputStream);
			byte[] imageData = byteArrayOutputStream.toByteArray();
			buf = ByteBuffer.allocateDirect(imageData.length);
			buf.put(imageData);
			buf.flip();

			try (MemoryStack memoryStack = MemoryStack.stackPush()){
				IntBuffer width = memoryStack.mallocInt(1);
				IntBuffer height = memoryStack.mallocInt(1);
				IntBuffer channels = memoryStack.mallocInt(1);
				ByteBuffer stbBuf = STBImage.stbi_load_from_memory(buf, width, height, channels, STBImage.STBI_rgb_alpha);
				if (stbBuf == null) {
					throw new IOException("Could not load image: " + STBImage.stbi_failure_reason());
				}
				return new STBLoadedImage(stbBuf, width.get(0), height.get(0), channels.get(0));
			}
		} finally {
			MemoryUtil.memFree(buf);
			IOUtils.closeQuietly(stream);
		}
	}

	public record SVGOptions(float width, float height) {
	}
}

package fr.theorozier.webstreamer.display.render;

import org.jetbrains.annotations.NotNull;

public class DisplayLayerSVGMap extends DisplayLayerMap<DisplayLayerSVGImage.SVGOptions> {
	private final DisplayLayerResources res;

	public DisplayLayerSVGMap(DisplayLayerResources res) {
		this.res = res;
	}

	@NotNull
	@Override
	protected DisplayLayerSVGImage.SVGOptions getLayerKey(Key key) {
		return new DisplayLayerSVGImage.SVGOptions(
			key.uri(),
			key.display().getWidth(),
			key.display().getHeight()
		);
	}

	@Override
	protected @NotNull DisplayLayerNode getNewLayer(Key key) throws OutOfLayerException, UnknownFormatException {
		return new DisplayLayerSVGImage(
				new DisplayLayerSVGImage.SVGOptions(
						key.uri(),
						key.display().getWidth(),
						key.display().getHeight()
				),
				this.res
		);
	}
}

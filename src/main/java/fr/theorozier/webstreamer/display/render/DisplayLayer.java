package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.source.Source;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

public class DisplayLayer extends RenderLayer {

    private static class Inner {

        private final Source source;
        private final DisplayTexture tex;

        private final FFmpegFrameGrabber grabber;

        Inner(Source source) {

            this.source = source;
            this.tex = new DisplayTexture();

            FFmpegFrameGrabber grabber = null;

            try {
                grabber = new FFmpegFrameGrabber("C:\\Users\\theor\\Downloads\\200.ts");
                grabber.start();
            } catch (FFmpegFrameGrabber.Exception e) {
                e.printStackTrace();
            }

            this.grabber = grabber;

        }

        private void tick() {

            try {

                Frame frame = this.grabber.grab();
                if (frame == null) {
                    this.grabber.setTimestamp(0);
                    frame = this.grabber.grab();
                }

                if (frame.image != null) {
                    this.tex.upload(frame);
                }

            } catch (FFmpegFrameGrabber.Exception e) {
                e.printStackTrace();
            }

        }

    }

    private final Inner inner;

    public DisplayLayer(Source source) {
        this(new Inner(source));
    }

    private DisplayLayer(Inner inner) {

        super("display", VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS,
                256, false, true,
                () -> {
                    inner.tick();
                    POSITION_TEXTURE_SHADER.startDrawing();
                    RenderSystem.enableTexture();
                    RenderSystem.setShaderTexture(0, inner.tex.getGlId());
                },
                () -> {

                });

        this.inner = inner;

    }

}

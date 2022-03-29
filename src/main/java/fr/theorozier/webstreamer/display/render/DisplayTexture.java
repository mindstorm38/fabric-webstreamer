package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import org.bytedeco.javacv.Frame;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

@Environment(EnvType.CLIENT)
public class DisplayTexture extends AbstractTexture {

    private int width = -1, height = -1;

    public DisplayTexture() {
        GlStateManager._bindTexture(this.getGlId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
    }

    private void resize(int width, int height) {
        if (this.width != width || this.height != height) {
            GlStateManager._bindTexture(this.getGlId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, width, height, 0, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, null);
            this.width = width;
            this.height = height;
        }
    }

    public void upload(Frame frame) {
        RenderSystem.assertOnRenderThread();
        if (frame.imageDepth == Frame.DEPTH_UBYTE && frame.imageChannels == 3) {
            ByteBuffer data = (ByteBuffer) frame.image[0];
            this.uploadRaw(data, frame.imageWidth, frame.imageHeight, frame.imageStride / 3);
        }
    }

    public void uploadRaw(ByteBuffer data, int width, int height, int dataWidth) {

        this.resize(width, height);

        GlStateManager._bindTexture(this.getGlId());
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, dataWidth);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);

        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, data);

    }

    @Override
    public void load(ResourceManager manager) { }

}

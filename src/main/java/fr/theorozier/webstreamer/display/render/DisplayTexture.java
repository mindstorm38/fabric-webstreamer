package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import org.bytedeco.javacv.Frame;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

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
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, null);
            this.width = width;
            this.height = height;
        }
    }

    public void upload(Frame frame) {

        if (frame.imageDepth != Frame.DEPTH_UBYTE || frame.imageChannels != 3)
            return;

        ByteBuffer imageBuf = (ByteBuffer) frame.image[0];

        int width = frame.imageWidth;
        int height = frame.imageHeight;

        this.resize(width, height);

        RenderSystem.assertOnRenderThread();
        GlStateManager._bindTexture(this.getGlId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, imageBuf);

    }

    @Override
    public void load(ResourceManager manager) { }

}

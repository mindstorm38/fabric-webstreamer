package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.mixin.WorldRendererInvoker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import org.joml.AxisAngle4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.net.URI;
import java.util.stream.StreamSupport;

@Environment(EnvType.CLIENT)
public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity> {
    
    private static final Text NO_LAYER_AVAILABLE_TEXT = Text.translatable("gui.webstreamer.display.status.noLayerAvailable");
    private static final Text UNKNOWN_FORMAT_TEXT = Text.translatable("gui.webstreamer.display.status.unknownFormat");
    private static final Text NO_URL_TEXT = Text.translatable("gui.webstreamer.display.status.noUrl");
    
    private static final Quaternionf ROTATE_90 = new Quaternionf(new AxisAngle4d(Math.PI / 2.0, 0.0, 1.0, 0.0));
    private static final Quaternionf ROTATE_180 = new Quaternionf(new AxisAngle4d(Math.PI, 0.0, 1.0, 0.0));
    private static final Quaternionf ROTATE_270 = new Quaternionf(new AxisAngle4d(Math.PI / 2.0 * 3.0, 0.0, 1.0, 0.0));

    private final GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
    private final TextRenderer textRenderer;
    
    @SuppressWarnings("unused")
    public DisplayBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(DisplayBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
    
        // Get the render data from the block entity, this is just an extension to the
        // block entity, so it will always be present and is lazily instantiated and will
        // last as long as the block entity.
        DisplayRenderData renderData = (DisplayRenderData) entity.getRenderData();
        if (renderData == null) {
            // If we are running on client side, we should NEVER get here.
            throw new IllegalStateException("null render data on client side is a programming error");
        }

        DisplayLayerManager layerManager = WebStreamerClientMod.DISPLAY_LAYERS;
        Text statusText = null;
        
        // Asynchronously get the URI of this display, if the URI is not yet available,
        // null is just returned.
        URI uri = renderData.getUri(layerManager.getResources().getExecutor());
        
        // If the player is currently holding a display item, we draw the outline shape
        // of the display block, we also display as status text the source status.
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
    
            boolean hasDisplayEquipped = StreamSupport.stream(player.getItemsEquipped().spliterator(), false)
                    .map(ItemStack::getItem)
                    .anyMatch(WebStreamerMod.DISPLAY_ITEM::equals);
    
            if (hasDisplayEquipped) {
        
                VoxelShape displayShape = entity.getCachedState().getOutlineShape(entity.getWorld(), entity.getPos());
                if (displayShape != null) {
                    matrices.push();
                    WorldRendererInvoker.drawCuboidShapeOutline(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), displayShape, 0, 0, 0, 235 / 255f, 168 / 255f, 0f, 1f);
                    matrices.pop();
                }
        
                statusText = Text.literal(entity.getSource().getStatus());
        
            }
            
        }
        
        if (uri != null) {
            try {
                
                DisplayLayerRender layer = layerManager.getRender(uri, entity);
    
                if (layer.isLost()) {
                    // Each time a display get here and the layer is lost, and then we request
                    // a URL reset for the render data. With twitch sources it should reset
                    // and re-fetch a new URL for the playlist.
                    entity.resetSourceUri();
                    return;
                }
                
                matrices.push();
                Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
    
                VertexConsumer buffer = vertexConsumers.getBuffer(layer.getRenderLayer());
    
                BlockPos pos = entity.getPos();
                float audioDistance = entity.getAudioDistance();
                float audioVolume = entity.getAudioVolume();
                layer.pushAudioSource(pos, pos.getManhattanDistance(this.gameRenderer.getCamera().getBlockPos()), audioDistance, audioVolume);
    
                // Width/Height start coords
                float ws = entity.getWidthOffset();
                float hs = entity.getHeightOffset();
                // Width/Height end coords
                float we = ws + entity.getWidth();
                float he = hs + entity.getHeight();
    
                switch (entity.getCachedState().get(Properties.HORIZONTAL_FACING)) {
                    case NORTH -> {
                        buffer.vertex(positionMatrix, we, hs, 0.95f).texture(0, 1).next();
                        buffer.vertex(positionMatrix, ws, hs, 0.95f).texture(1, 1).next();
                        buffer.vertex(positionMatrix, ws, he, 0.95f).texture(1, 0).next();
                        buffer.vertex(positionMatrix, we, he, 0.95f).texture(0, 0).next();
                    }
                    case SOUTH -> {
                        buffer.vertex(positionMatrix, ws, hs, 0.05f).texture(0, 1).next();
                        buffer.vertex(positionMatrix, we, hs, 0.05f).texture(1, 1).next();
                        buffer.vertex(positionMatrix, we, he, 0.05f).texture(1, 0).next();
                        buffer.vertex(positionMatrix, ws, he, 0.05f).texture(0, 0).next();
                    }
                    case EAST -> {
                        buffer.vertex(positionMatrix, 0.05f, hs, we).texture(0, 1).next();
                        buffer.vertex(positionMatrix, 0.05f, hs, ws).texture(1, 1).next();
                        buffer.vertex(positionMatrix, 0.05f, he, ws).texture(1, 0).next();
                        buffer.vertex(positionMatrix, 0.05f, he, we).texture(0, 0).next();
                    }
                    case WEST -> {
                        buffer.vertex(positionMatrix, 0.95f, hs, ws).texture(0, 1).next();
                        buffer.vertex(positionMatrix, 0.95f, hs, we).texture(1, 1).next();
                        buffer.vertex(positionMatrix, 0.95f, he, we).texture(1, 0).next();
                        buffer.vertex(positionMatrix, 0.95f, he, ws).texture(0, 0).next();
                    }
                    default -> throw new IllegalArgumentException();
                }
    
                matrices.pop();
                
            } catch (DisplayLayerManager.OutOfLayerException e) {
                statusText = NO_LAYER_AVAILABLE_TEXT;
            } catch (DisplayLayerManager.UnknownFormatException e) {
                statusText = UNKNOWN_FORMAT_TEXT;
            }
        } else {
            statusText = NO_URL_TEXT;
        }
    
        if (statusText != null) {
    
            matrices.push();
    
            final float scaleFactor = 128f / Math.min(entity.getWidth(), entity.getHeight());
            final float scale = 1f / scaleFactor;
            final float halfWidth = this.textRenderer.getWidth(statusText) / scaleFactor / 2f;
            final float halfHeight = this.textRenderer.fontHeight / scaleFactor / 2f;
    
            switch (entity.getCachedState().get(Properties.HORIZONTAL_FACING)) {
                case NORTH -> {
                    matrices.translate(0.5f + halfWidth, 0.5f + halfHeight, 0.85f);
                }
                case SOUTH -> {
                    matrices.multiply(ROTATE_180);
                    matrices.translate(-0.5f + halfWidth, 0.5f + halfHeight, -0.15f);
                }
                case EAST -> {
                    matrices.multiply(ROTATE_270);
                    matrices.translate(0.5f + halfWidth, 0.5f + halfHeight, -0.15f);
                }
                case WEST -> {
                    matrices.multiply(ROTATE_90);
                    matrices.translate(-0.5f + halfWidth, 0.5f + halfHeight, 0.85f);
                }
                default -> throw new IllegalArgumentException();
            }
    
            matrices.scale(-scale, -scale, 1f);
            this.textRenderer.draw(statusText, 0f, 0f, 0x00ffffff, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextLayerType.NORMAL, 0xBB222222, light);
            matrices.pop();
    
        }

    }

}

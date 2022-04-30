package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import fr.theorozier.webstreamer.mixin.WorldRendererInvoker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.shape.VoxelShape;

import java.util.stream.StreamSupport;

@Environment(EnvType.CLIENT)
public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity> {
    
    private static final Text NO_LAYER_AVAILABLE_TEXT = new TranslatableText("gui.webstreamer.display.status.noLayerAvailable");
    private static final Text NO_URL_TEXT = new TranslatableText("gui.webstreamer.display.status.noUrl");
    
    private final GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
    private final TextRenderer textRenderer;
    
    @SuppressWarnings("unused")
    public DisplayBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(DisplayBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
    
        DisplayRenderData renderData = (DisplayRenderData) entity.getRenderData();
        DisplayLayerManager layerManager = WebStreamerClientMod.DISPLAY_LAYERS;
        
        DisplayUrl url = renderData.getUrl(layerManager.getResources().getExecutor());
    
        PlayerEntity player = MinecraftClient.getInstance().player;
        
        Text statusText = null;
        
        if (player != null) {
    
            boolean hasDisplayEquipped = StreamSupport.stream(player.getItemsEquipped().spliterator(), false)
                    .map(ItemStack::getItem)
                    .anyMatch(WebStreamerMod.DISPLAY_ITEM::equals);
    
            if (hasDisplayEquipped) {
        
                VoxelShape displayShape = entity.getCachedState().getOutlineShape(entity.getWorld(), entity.getPos());
                if (displayShape != null) {
                    matrices.push();
                    WorldRendererInvoker.drawShapeOutline(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), displayShape, 0, 0, 0, 235 / 255f, 168 / 255f, 0f, 1f);
                    matrices.pop();
                }
        
                statusText = new LiteralText(entity.getSource().getStatus());
        
            }
            
        }
        
        if (url != null) {
            DisplayLayerHls layer = layerManager.forSource(url);
            if (layer != null) {
    
                matrices.push();
                Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

                VertexConsumer buffer = vertexConsumers.getBuffer(layer);

                BlockPos pos = entity.getPos();
                float audioDistance = entity.getAudioDistance();
                float audioVolume = entity.getAudioVolume();
                layer.pushAudioSource(pos, pos.getManhattanDistance(this.gameRenderer.getCamera().getBlockPos()), audioDistance, audioVolume);

                // Width/Height start coords
                float ws = renderData.getWidthOffset();
                float hs = renderData.getHeightOffset();
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
                }
                
                matrices.pop();
                
            } else {
                statusText = NO_LAYER_AVAILABLE_TEXT;
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
                case NORTH -> matrices.translate(0.5f + halfWidth, 0.5f + halfHeight, 0.85f);
                case SOUTH -> {
                    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180));
                    matrices.translate(-0.5f + halfWidth, 0.5f + halfHeight, -0.15f);
                }
                case EAST -> {
                    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(270));
                    matrices.translate(0.5f + halfWidth, 0.5f + halfHeight, -0.15f);
                }
                case WEST -> {
                    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(90));
                    matrices.translate(-0.5f + halfWidth, 0.5f + halfHeight, 0.85f);
                }
            }
    
            matrices.scale(-scale, -scale, 1f);
            this.textRenderer.draw(statusText, 0f, 0f, 0x00ffffff, false, matrices.peek().getPositionMatrix(), vertexConsumers, false, 0xBB222222, light);
            matrices.pop();
    
        }

    }

}

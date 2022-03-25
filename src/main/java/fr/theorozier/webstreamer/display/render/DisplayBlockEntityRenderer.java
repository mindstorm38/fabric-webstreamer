package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.source.Source;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;

@Environment(EnvType.CLIENT)
public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity> {

    public DisplayBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {

    }

    @Override
    public void render(DisplayBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {

        Source source = entity.getSource();

        if (source != null) {

            matrices.push();

            MatrixStack.Entry entry = matrices.peek();
            Matrix4f positionMatrix = entry.getPositionMatrix();

            DisplayLayer layer = WebStreamerClientMod.DISPLAY_LAYERS.forSource(source);
            VertexConsumer buffer = vertexConsumers.getBuffer(layer);

            buffer.vertex(positionMatrix, 0, 0, 0).texture(0, 0).next();
            buffer.vertex(positionMatrix, 0, 0, 1).texture(0, 1).next();
            buffer.vertex(positionMatrix, 0, 1, 1).texture(1, 1).next();
            buffer.vertex(positionMatrix, 0, 1, 0).texture(1, 0).next();

            matrices.pop();

        }

    }

}

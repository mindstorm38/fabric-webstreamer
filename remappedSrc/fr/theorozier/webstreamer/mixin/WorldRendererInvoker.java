package fr.theorozier.webstreamer.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public interface WorldRendererInvoker {
	
	@Invoker("drawCuboidShapeOutline")
	static void drawCuboidShapeOutline(MatrixStack matrices, VertexConsumer vertexConsumer, VoxelShape voxelShape, double dx, double dy, double dz, float r, float g, float b, float a) {
		throw new AssertionError();
	}
	
}

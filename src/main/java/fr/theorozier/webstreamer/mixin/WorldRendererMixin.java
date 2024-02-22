package fr.theorozier.webstreamer.mixin;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	
	@Inject(at = @At("HEAD"), method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V")
	public void render(CallbackInfo info) {
		WebStreamerClientMod.DISPLAY_LAYERS.tick();
	}

	@Inject(at = @At("HEAD"), method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V")
	public void setWorld(@Nullable ClientWorld world, CallbackInfo info) {
		if (world == null) {
			// Zero is special value for forcing cleanup.
			if (!WebStreamerClientMod.DISPLAY_LAYERS.cleanup(0)) {
				throw new IllegalStateException("cleanup with special zero value should be successful");
			}
		}
	}
	
}

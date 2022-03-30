package fr.theorozier.webstreamer.mixin;

import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.DisplayBlockInteract;
import fr.theorozier.webstreamer.display.screen.DisplayBlockScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin implements DisplayBlockInteract {

    @SuppressWarnings("all")
    private ClientPlayerEntity getThis() {
        return (ClientPlayerEntity) (Object) this;
    }

    @Override
    public void openDisplayBlockScreen(DisplayBlockEntity blockEntity) {
        MinecraftClient.getInstance().setScreen(new DisplayBlockScreen(blockEntity));
    }

}

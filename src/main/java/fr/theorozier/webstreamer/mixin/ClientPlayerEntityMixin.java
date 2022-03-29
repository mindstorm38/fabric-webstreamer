package fr.theorozier.webstreamer.mixin;

import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.DisplayBlockInteract;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin implements DisplayBlockInteract {

    @Override
    public void openDisplayBlockScreen(DisplayBlockEntity blockEntity) {
        ((ClientPlayerEntity) (Object) this).sendChatMessage("test");
    }

}

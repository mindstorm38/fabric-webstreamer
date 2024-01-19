package fr.theorozier.webstreamer.display;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.BiConsumer;

/**
 * <p>This static class provides methods to send display block update when editing one,
 * from client to server. Note that the opposite packet from server to client is 
 * implemented natively by the game so we don't need to implement it.</p>
 */
public class DisplayNetworking {
	
	public static final Identifier DISPLAY_BLOCK_UPDATE_PACKET_ID = new Identifier("webstreamer:display_block_update");
	
	private static PacketByteBuf encodeDisplayUpdatePacket(DisplayBlockEntity blockEntity) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(blockEntity.getPos());
		NbtCompound comp = new NbtCompound();
		blockEntity.writeNbt(comp);
		buf.writeNbt(comp);
		return buf;
	}
	
	private static void decodeDisplayUpdatePacket(PacketByteBuf buf, BiConsumer<BlockPos, NbtCompound> consumer) {
		BlockPos pos = buf.readBlockPos();
		NbtCompound nbt = buf.readNbt();
		consumer.accept(pos, nbt);
	}
	
	/**
	 * Client-side only, send a display update packet to the server.
	 * @param blockEntity The display block entity.
	 */
	@Environment(EnvType.CLIENT)
	public static void sendDisplayUpdate(DisplayBlockEntity blockEntity) {
		ClientPlayNetworking.send(DISPLAY_BLOCK_UPDATE_PACKET_ID, encodeDisplayUpdatePacket(blockEntity));
	}
	
	/**
	 * Server-side (integrated or dedicated) display packet receiver.
	 */
	public static void registerDisplayUpdateReceiver() {
		ServerPlayNetworking.registerGlobalReceiver(DISPLAY_BLOCK_UPDATE_PACKET_ID, new DisplayUpdateHandler());
	}
	
	private static class DisplayUpdateHandler implements ServerPlayNetworking.PlayChannelHandler {
		
		@Override
		public void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
			if (DisplayBlock.canUse(player)) {
				decodeDisplayUpdatePacket(buf, (pos, nbt) -> {
					ServerWorld world = player.getServerWorld();
					world.getServer().executeSync(() -> {
						if (world.getBlockEntity(pos) instanceof DisplayBlockEntity blockEntity) {
							blockEntity.readNbt(nbt);
							blockEntity.markDirty();
							world.updateListeners(pos, blockEntity.getCachedState(), blockEntity.getCachedState(), Block.NOTIFY_ALL);
						}
					});
				});
			}
		}
	}
	
}

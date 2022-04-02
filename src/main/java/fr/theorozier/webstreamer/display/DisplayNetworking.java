package fr.theorozier.webstreamer.display;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.BiConsumer;

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
	
	@Environment(EnvType.CLIENT)
	public static class Client {
		
		public static void sendDisplayUpdate(DisplayBlockEntity blockEntity) {
			ClientPlayNetworking.send(DISPLAY_BLOCK_UPDATE_PACKET_ID, encodeDisplayUpdatePacket(blockEntity));
		}
		
	}
	
	public static class Server {
		
		public static void registerDisplayUpdateReceiver() {
			ServerPlayNetworking.registerGlobalReceiver(DISPLAY_BLOCK_UPDATE_PACKET_ID, new DisplayUpdateHandler());
		}
		
		private static class DisplayUpdateHandler implements ServerPlayNetworking.PlayChannelHandler {
			
			@Override
			public void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
				if (player.hasPermissionLevel(DisplayBlock.PERMISSION_LEVEL)) {
					decodeDisplayUpdatePacket(buf, (pos, nbt) -> {
						ServerWorld world = player.getWorld();
						world.getServer().executeSync(() -> {
							if (world.getBlockEntity(pos) instanceof DisplayBlockEntity blockEntity) {
								blockEntity.readNbt(nbt);
								blockEntity.markDirty();
							}
						});
					});
				}
			}
		}
	
	}
	
}

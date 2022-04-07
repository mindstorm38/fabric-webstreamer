package fr.theorozier.webstreamer.display;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.jetbrains.annotations.Nullable;

public class DisplayBlockItem extends BlockItem {
	
	public DisplayBlockItem(Block block, Settings settings) {
		super(block, settings);
	}
	
	@Nullable
	@Override
	protected BlockState getPlacementState(ItemPlacementContext context) {
		PlayerEntity playerEntity = context.getPlayer();
		return playerEntity != null && !DisplayBlock.canPlace(playerEntity) ? null : super.getPlacementState(context);
	}
	
}

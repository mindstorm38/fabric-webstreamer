package fr.theorozier.webstreamer.display;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class DisplayBlock extends BlockWithEntity {

    private static final VoxelShape SHAPE_NORTH = VoxelShapes.cuboid(0, 0, 0.9, 1, 1, 1);
    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.cuboid(0, 0, 0, 1, 1, 0.1);
    private static final VoxelShape SHAPE_WEST = VoxelShapes.cuboid(0.9, 0, 0, 1, 1, 1);
    private static final VoxelShape SHAPE_EAST = VoxelShapes.cuboid(0, 0, 0, 0.1, 1, 1);
    
    public DisplayBlock() {
        super(Settings.of(Material.GLASS)
                .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                .breakInstantly()
                .noCollision()
                .nonOpaque());
    }
    
    

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }
    
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getPlayerFacing().getOpposite());
    }
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(Properties.HORIZONTAL_FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> null;
        };
    }
    
}

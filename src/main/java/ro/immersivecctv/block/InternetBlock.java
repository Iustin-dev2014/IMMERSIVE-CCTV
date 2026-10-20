package ro.immersivecctv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ro.immersivecctv.blockentity.InternetBlockEntity;

/** In-world fibre distribution point with five individually selectable optical ports. */
public final class InternetBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);
    public InternetBlock(Properties properties) { super(properties); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new InternetBlockEntity(pos, state); }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof InternetBlockEntity internet) {
            for (int port = 0; port < 5; port++) {
                BlockPos routerPos = internet.getRouter(port);
                if (routerPos != null && level.getBlockEntity(routerPos) instanceof ro.immersivecctv.blockentity.RouterBlockEntity router
                        && pos.equals(router.getInternetBlockPos())) {
                    router.disconnectFibre();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

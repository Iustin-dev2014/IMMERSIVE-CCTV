package ro.immersivecctv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ro.immersivecctv.LinkingManager;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import ro.immersivecctv.client.ClientHooks;

public class MonitorBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 1, 5, 13, 2, 13), Block.box(7, 2, 7, 9, 6, 9),
            Block.box(1, 5, 2, 15, 15, 5), Block.box(6, 5, 5, 10, 7, 7),
            // Rear AC power socket provides a separate physical connection from HDMI.
            Block.box(6, 5, 14, 10, 7, 16.5));

    public MonitorBlock(Properties properties) { super(properties); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MonitorBlockEntity(pos, state); }

    @Override
    public void onRemove(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            BlockPos xvrPos = monitor.getXvrPos();
            if (xvrPos != null && level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr
                    && pos.equals(xvr.getMonitorPos())) {
                xvr.setMonitorPos(null);
            }
            monitor.setXvrPos(null);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private boolean hasPhysicalHdmi(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) || monitor.getXvrPos() == null) return false;
        return level.getBlockEntity(monitor.getXvrPos()) instanceof XvrBlockEntity xvr
                && pos.equals(xvr.getMonitorPos());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) LinkingManager.disconnectMonitor(player, level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity poweredMonitor) || !poweredMonitor.hasWorkingPower()) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.literal("[CCTV] Monitor fara alimentare. Conecteaza un cablu de alimentare la o priza.") , true);
            return InteractionResult.PASS;
        }
        if (!hasPhysicalHdmi(level, pos)) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.literal("[CCTV] Monitorul nu are cablu HDMI conectat la ambele mufe."), true);
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openMonitorConsole(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

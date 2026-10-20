package ro.immersivecctv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import ro.immersivecctv.ImmersiveCCTV;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ro.immersivecctv.LinkingManager;
import ro.immersivecctv.blockentity.CameraBlockEntity;

public class CameraBlock extends Block implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 8, 1, 12, 14, 3), Block.box(6, 7, 3, 10, 10, 7),
            Block.box(3, 5, 6, 13, 10, 14), Block.box(2, 9, 5, 14, 11, 13),
            Block.box(5, 6, 3, 11, 9, 6),
            // Symmetric click surfaces allow the rear connector to be selected in every rotated facing.
            Block.box(6, 6, 0, 10, 8, 2), Block.box(6, 6, 14, 10, 8, 16),
            Block.box(0, 6, 6, 2, 8, 10), Block.box(14, 6, 6, 16, 8, 10));

    public CameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CameraBlockEntity(pos, state); }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
            BlockPos xvrPos = camera.getXvrPos();
            if (xvrPos != null && level.getBlockEntity(xvrPos) instanceof ro.immersivecctv.blockentity.XvrBlockEntity xvr) {
                xvr.removeCamera(pos);
            }
            camera.setXvrPos(null);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.getItemInHand(hand).is(ImmersiveCCTV.CCTV_SERVICE_TOOL.get())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
                player.sendSystemMessage(Component.literal("[CCTV SERVICE TOOL] Camera ID: " + camera.getCameraId()));
                player.sendSystemMessage(Component.literal("Username: " + camera.getCredentialUsername()));
                if (camera.hasChangedPassword()) {
                    player.sendSystemMessage(Component.literal("Password: changed (not readable). Use the configured password to authenticate."));
                } else {
                    player.sendSystemMessage(Component.literal("Default password: 0000 — change it immediately after the first successful login."));
                }
                player.sendSystemMessage(Component.literal("Connect this camera to the XVR with a PoE Cat6 cable, then enter the credentials in the XVR console."));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) LinkingManager.disconnectCamera(player, level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}

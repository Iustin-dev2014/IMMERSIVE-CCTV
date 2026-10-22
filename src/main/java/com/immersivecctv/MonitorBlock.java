package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class MonitorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 4, 16, 15, 12);
    private static final VoxelShape SHAPE_EW = Block.box(4, 0, 0, 12, 15, 16);

    public MonitorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof CableItem) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity mon)) {
            return InteractionResult.PASS;
        }
        Direction facing = state.getValue(FACING);
        Vec3 l = Ports.hitLocal(hit.getLocation(), pos, facing);

        if (hit.getDirection() == facing.getOpposite()) {
            if (!level.isClientSide) {
                if (Ports.monHdmiAt(l)) {
                    BlockPos xp = mon.getXvrPos();
                    if (xp != null && level.getBlockEntity(xp) instanceof XvrBlockEntity xvr) {
                        xvr.disconnectHdmi();
                        mon.setXvrPos(null);
                        CableItem.give(player, ModRegistry.HDMI_CABLE.get());
                        player.displayClientMessage(Component.literal("HDMI unplugged."), true);
                    } else {
                        player.displayClientMessage(Component.literal("HDMI port is empty."), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (hit.getDirection() == facing && Ports.monButtonAt(l)) {
            if (!level.isClientSide) {
                mon.toggleScreen();
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F,
                        mon.isScreenOn() ? 1.3F : 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            if (!mon.isScreenOn()) {
                player.displayClientMessage(Component.literal("The monitor is off. Press its power button."), true);
            } else if (mon.getXvrPos() == null) {
                player.displayClientMessage(Component.literal("No HDMI cable connected."), true);
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openScreen(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

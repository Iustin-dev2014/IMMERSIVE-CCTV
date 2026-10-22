package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class XvrBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 2, 15, 6, 14);
    private static final VoxelShape SHAPE_EW = Block.box(2, 0, 1, 14, 6, 15);

    public XvrBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(POWERED, Boolean.FALSE);
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
        return new XvrBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.XVR_BE.get(), XvrBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof CableItem) {
            return InteractionResult.PASS;
        }
        Direction facing = state.getValue(FACING);
        Vec3 l = Ports.hitLocal(hit.getLocation(), pos, facing);

        if (hit.getDirection() == facing) {
            if (!level.isClientSide) {
                if (Ports.xvrButtonAt(l)) {
                    if (level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
                        xvr.togglePower();
                    }
                } else {
                    player.displayClientMessage(Component.literal("Press the round power button."), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (hit.getDirection() == facing.getOpposite()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
                int lan = Ports.xvrLanAt(l);
                if (lan >= 0) {
                    if (xvr.disconnectLan(lan)) {
                        CableItem.give(player, ModRegistry.ETHERNET_CABLE.get());
                        player.displayClientMessage(Component.literal("LAN " + (lan + 1) + " unplugged."), true);
                    } else {
                        player.displayClientMessage(Component.literal("LAN " + (lan + 1) + " is empty."), true);
                    }
                } else if (Ports.xvrHdmiAt(l)) {
                    if (xvr.disconnectHdmi()) {
                        CableItem.give(player, ModRegistry.HDMI_CABLE.get());
                        player.displayClientMessage(Component.literal("HDMI unplugged."), true);
                    } else {
                        player.displayClientMessage(Component.literal("HDMI port is empty."), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}

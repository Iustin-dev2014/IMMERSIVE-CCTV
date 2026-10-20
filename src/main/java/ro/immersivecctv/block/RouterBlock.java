package ro.immersivecctv.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ro.immersivecctv.CctvSounds;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.blockentity.RouterBlockEntity;

/** Fixed-orientation desktop router with state-backed power, fibre uplink and four LAN link LEDs. */
public final class RouterBlock extends Block implements EntityBlock {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty INTERNET = BooleanProperty.create("internet");
    public static final IntegerProperty LAN_MASK = IntegerProperty.create("lan_mask", 0, 15);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 1, 2, 15, 7, 14),
            Block.box(2, 0, 3, 4, 1, 5), Block.box(12, 0, 3, 14, 1, 5),
            Block.box(2, 0, 11, 4, 1, 13), Block.box(12, 0, 11, 14, 1, 13));

    public RouterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(INTERNET, false).setValue(LAN_MASK, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, INTERNET, LAN_MASK);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RouterBlockEntity(pos, state); }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            router.disconnectFibre();
            for (int port = 0; port < 4; port++) router.disconnectXvr(port);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        // The physical power button is on the front (north) edge of the router.
        double x = hit.getLocation().x - pos.getX();
        double y = hit.getLocation().y - pos.getY();
        if (hit.getDirection() != Direction.NORTH || x < 0.70 || x > 0.96 || y < 0.09 || y > 0.25) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockEntity raw = level.getBlockEntity(pos);
            if (!(raw instanceof RouterBlockEntity router)) return InteractionResult.CONSUME;
            boolean powered = state.getValue(POWERED);
            if (!powered && !router.hasWorkingPower()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "[ROUTER] Needs an AC Charger 12V, Power Cable and a powered wall outlet."), true);
                return InteractionResult.CONSUME;
            }
            level.setBlock(pos, state.setValue(POWERED, !powered).setValue(INTERNET, !powered && router.hasInternet()), 3);
            if (powered) level.setBlock(pos, level.getBlockState(pos).setValue(LAN_MASK, 0), 3);
            level.playSound(null, pos, CctvSounds.get(powered ? "xvr_power_off" : "xvr_power_on"), net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, powered ? 0.82F : 1.12F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(powered
                    ? "[ROUTER] Powered off. Network links are down."
                    : (router.hasInternet() ? "[ROUTER] Started. Fibre signal acquired; Internet available."
                    : "[ROUTER] Started. SIGNAL LOSS: connect the optical-fibre uplink to an Internet Block.")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ImmersiveCCTV.ROUTER_BE.get()) return null;
        // T is generic here; perform a runtime check instead of an invalid cast/method reference.
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof RouterBlockEntity router) {
                RouterBlockEntity.serverTick(tickLevel, tickPos, tickState, router);
            }
        };
    }
}

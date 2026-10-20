package ro.immersivecctv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import ro.immersivecctv.CctvSounds;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ro.immersivecctv.LinkingManager;
import ro.immersivecctv.blockentity.XvrBlockEntity;

public class XvrBlock extends Block implements EntityBlock {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty POE_LINK = BooleanProperty.create("poe_link");
    public static final BooleanProperty WAN_LINK = BooleanProperty.create("wan_link");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 5, 1, 5), Block.box(11, 0, 2, 14, 1, 5),
            Block.box(2, 0, 11, 5, 1, 14), Block.box(11, 0, 11, 14, 1, 14),
            Block.box(1, 1, 1, 15, 6, 15),
            // Rear HDMI, USB-A and 12V DC sockets protrude just beyond the chassis.
            Block.box(5, 2, 14, 11, 4, 16.75), Block.box(11, 2, 14, 13, 4, 16.2),
            Block.box(13, 2, 14, 15, 4, 16.2),
            // Dedicated rear WAN port, distinct from the eight front-facing PoE camera jacks.
            Block.box(6, 4, 14, 10, 6, 16.75));

    public XvrBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(POE_LINK, false).setValue(WAN_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POWERED, POE_LINK, WAN_LINK); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new XvrBlockEntity(pos, state); }

    @javax.annotation.Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ro.immersivecctv.ImmersiveCCTV.XVR_BE.get()) return null;
        // T is generic here; perform a runtime check instead of an invalid cast/method reference.
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof XvrBlockEntity xvr) {
                XvrBlockEntity.serverTick(tickLevel, tickPos, tickState, xvr);
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) LinkingManager.disconnectAll(player, level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!LinkingManager.isPowerButton(pos, hit.getDirection(), hit.getLocation())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("[XVR] Cloud IP: " + xvr.getCloudIp()), false);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("[XVR] Cloud Port: " + xvr.getCloudPort()), false);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(xvr.canObtainCloudEndpoint()
                        ? "[XVR] Cloud route online (simulated in-world network)."
                        : "[XVR] Host unreachable. Check XVR power, router WAN, router power and fibre uplink."), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            boolean powered = state.getValue(POWERED);
            if (!powered && (!(level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) || !xvr.has12VAdapter() || !xvr.hasWorkingPower())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("[XVR] Lipseste AC Charger 12V sau cablul de alimentare de la priza."), true);
                return InteractionResult.CONSUME;
            }
            level.setBlock(pos, state.setValue(POWERED, !powered), 3);
            level.playSound(null, pos, CctvSounds.get(powered ? "xvr_power_off" : "xvr_power_on"), SoundSource.BLOCKS, 0.82F, powered ? 0.82F : 1.0F);
            level.playSound(null, pos, CctvSounds.randomVariant("relay_click_", 44), SoundSource.BLOCKS, 0.58F, powered ? 0.75F : 1.15F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(!powered
                    ? "[XVR] Sistem pornit. HDD si porturi PoE initializate."
                    : "[XVR] Sistem oprit."), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

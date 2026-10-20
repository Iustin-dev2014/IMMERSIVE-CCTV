package ro.immersivecctv.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ro.immersivecctv.CctvSounds;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.blockentity.InternetBlockEntity;
import ro.immersivecctv.blockentity.RouterBlockEntity;

/** Physical optical-fibre uplink from a router's fibre jack to one of five Internet Block ports. */
public final class FiberOpticCableItem extends Item {
    private static final Map<UUID, BlockPos> PENDING_ROUTER = new ConcurrentHashMap<>();
    public FiberOpticCableItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel(); Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos(); Vec3 hit = context.getClickLocation();
        if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            boolean fibreJack = context.getClickedFace() == Direction.SOUTH && hit.z - pos.getZ() >= 0.86
                    && hit.x - pos.getX() >= 0.80 && hit.x - pos.getX() <= 0.96
                    && hit.y - pos.getY() >= 0.16 && hit.y - pos.getY() <= 0.56;
            if (!fibreJack) {
                player.displayClientMessage(Component.literal("[FIBRE] Click the dedicated optical-fibre jack on the router's rear panel."), true);
                return InteractionResult.FAIL;
            }
            if (router.getInternetBlockPos() != null) {
                player.displayClientMessage(Component.literal("[FIBRE] This router already has an optical uplink. Disconnect it before rewiring."), true);
                return InteractionResult.FAIL;
            }
            PENDING_ROUTER.put(player.getUUID(), pos.immutable());
            player.displayClientMessage(Component.literal("[FIBRE] Router fibre jack selected. Now click one of the five ports on the top of the Internet Block."), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof InternetBlockEntity internet) {
            BlockPos routerPos = PENDING_ROUTER.get(player.getUUID());
            if (routerPos == null) {
                player.displayClientMessage(Component.literal("[FIBRE] First select the router's optical jack with this cable."), true);
                return InteractionResult.FAIL;
            }
            int port = selectPort(pos, context.getClickedFace(), hit);
            if (port < 0) {
                player.displayClientMessage(Component.literal("[FIBRE] Select a marked port on the TOP surface of the Internet Block."), true);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                if (!(level.getBlockEntity(routerPos) instanceof RouterBlockEntity router)) {
                    PENDING_ROUTER.remove(player.getUUID());
                    player.displayClientMessage(Component.literal("[FIBRE] The selected router no longer exists."), true);
                    return InteractionResult.FAIL;
                }
                if (!router.connectFibre(pos, port)) {
                    player.displayClientMessage(Component.literal("[FIBRE] Port is occupied, or the router already has an uplink."), true);
                    return InteractionResult.FAIL;
                }
                PENDING_ROUTER.remove(player.getUUID());
                level.playSound(null, pos, CctvSounds.get("hdmi_connect"), SoundSource.BLOCKS, 0.72F, 1.55F);
                player.displayClientMessage(Component.literal("[FIBRE] Connected to Internet Block port " + (port + 1) + ". The red SIGNAL LOSS LED turns off when the router is powered."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static int selectPort(BlockPos pos, Direction face, Vec3 hit) {
        if (face != Direction.UP) return -1;
        double x = hit.x - pos.getX(), z = hit.z - pos.getZ();
        if (z < 0.18 || z > 0.82) return -1;
        double[] centers = {0.12, 0.31, 0.50, 0.69, 0.88};
        for (int i = 0; i < centers.length; i++) if (Math.abs(x - centers[i]) <= 0.075) return i;
        return -1;
    }
}

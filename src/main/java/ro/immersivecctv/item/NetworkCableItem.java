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
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.CctvSounds;
import ro.immersivecctv.blockentity.RouterBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Two-ended Ethernet patch lead: choose one router LAN socket, then the XVR's rear WAN socket. */
public final class NetworkCableItem extends Item {
    private record Pending(BlockPos routerPos, int lanPort) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    public NetworkCableItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel(); Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos(); Vec3 hit = context.getClickLocation();
        if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            int port = routerPort(pos, context.getClickedFace(), hit);
            if (port < 0) {
                player.displayClientMessage(Component.literal("[NETWORK] Click a LAN 1-4 socket on the router's rear panel."), true);
                return InteractionResult.FAIL;
            }
            PENDING.put(player.getUUID(), new Pending(pos.immutable(), port));
            player.displayClientMessage(Component.literal("[NETWORK] Router LAN " + (port + 1) + " selected. Now click the XVR's separate WAN socket on its rear panel."), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
            Pending pending = PENDING.get(player.getUUID());
            if (pending == null) {
                player.displayClientMessage(Component.literal("[NETWORK] First select a router LAN socket with this cable."), true);
                return InteractionResult.FAIL;
            }
            boolean port = context.getClickedFace() == Direction.SOUTH && hit.z - pos.getZ() >= 0.88
                    && hit.x - pos.getX() >= 0.36 && hit.x - pos.getX() <= 0.64
                    && hit.y - pos.getY() >= 0.25 && hit.y - pos.getY() <= 0.43;
            if (!port) {
                player.displayClientMessage(Component.literal("[NETWORK] That's not the XVR WAN socket. Click the dedicated rear RJ45 jack."), true);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                if (!(level.getBlockEntity(pending.routerPos()) instanceof RouterBlockEntity router)) {
                    PENDING.remove(player.getUUID());
                    player.displayClientMessage(Component.literal("[NETWORK] The selected router no longer exists."), true);
                    return InteractionResult.FAIL;
                }
                if (xvr.getRouterPos() != null && level.getBlockEntity(xvr.getRouterPos()) instanceof RouterBlockEntity oldRouter) {
                    oldRouter.disconnectXvr(xvr.getRouterLanPort());
                }
                if (!router.connectXvr(pending.lanPort(), pos)) {
                    player.displayClientMessage(Component.literal("[NETWORK] That LAN socket is already occupied, or the XVR is connected to another port."), true);
                    return InteractionResult.FAIL;
                }
                PENDING.remove(player.getUUID());
                level.playSound(null, pos, CctvSounds.get("hdmi_connect"), SoundSource.BLOCKS, 0.62F, 1.35F);
                player.displayClientMessage(Component.literal("[NETWORK] Ethernet connected: router LAN " + (pending.lanPort() + 1) + " -> XVR WAN."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static int routerPort(BlockPos pos, Direction face, Vec3 hit) {
        if (face != Direction.SOUTH || hit.z - pos.getZ() < 0.86) return -1;
        double x = hit.x - pos.getX(), y = hit.y - pos.getY();
        if (y < 0.16 || y > 0.52) return -1;
        double[] centers = {0.11, 0.30, 0.49, 0.68};
        for (int i = 0; i < centers.length; i++) if (Math.abs(x - centers[i]) <= 0.075) return i;
        return -1;
    }
}

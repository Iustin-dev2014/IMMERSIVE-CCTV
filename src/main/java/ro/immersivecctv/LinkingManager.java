package ro.immersivecctv;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ro.immersivecctv.CctvSounds;
import ro.immersivecctv.block.CameraBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import ro.immersivecctv.item.CctvCableItem;

/** Strict physical-port linking. Connections can only be made with the matching cable item. */
public final class LinkingManager {
    public enum Endpoint { MONITOR, CAMERA }
    private record Pending(Endpoint endpoint, BlockPos pos, CctvCableItem.Kind kind) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private LinkingManager() {}

    public static boolean isMonitorHdmiPort(BlockPos pos, Direction face, Vec3 hit) {
        Vec3 p = local(pos, hit);
        // The HDMI socket is centered on the rear panel of the monitor.
        return face == Direction.SOUTH && p.x >= 0.36 && p.x <= 0.64
                && p.y >= 0.30 && p.y <= 0.45;
    }

    public static boolean isCameraPoePort(Level level, BlockPos pos, Direction face, Vec3 hit) {
        BlockEntity be = level.getBlockEntity(pos);
        Direction rear = Direction.SOUTH;
        if (be instanceof CameraBlockEntity && level.getBlockState(pos).hasProperty(CameraBlock.FACING)) {
            rear = level.getBlockState(pos).getValue(CameraBlock.FACING).getOpposite();
        }
        Vec3 p = local(pos, hit);
        boolean centered = (face == Direction.NORTH || face == Direction.SOUTH)
                ? p.x >= 0.36 && p.x <= 0.64
                : p.z >= 0.36 && p.z <= 0.64;
        return face == rear && centered && p.y >= 0.35 && p.y <= 0.53;
    }

    public static boolean isXvrHdmiPort(Direction face, BlockPos pos, Vec3 hit) {
        Vec3 p = local(pos, hit);
        return face == Direction.SOUTH && p.x >= 0.31 && p.x <= 0.69
                && p.y >= 0.09 && p.y <= 0.29 && p.z >= 0.90;
    }

    public static boolean isXvrPoePort(Direction face, BlockPos pos, Vec3 hit) {
        if (face != Direction.NORTH) return false;
        Vec3 p = local(pos, hit);
        if (p.y < 0.10 || p.y > 0.25 || p.z > 0.10) return false;
        // Centers of the eight physically modelled RJ45 sockets on the front panel.
        for (int i = 0; i < 8; i++) {
            double centerX = 0.1309 + i * 0.0957;
            if (Math.abs(p.x - centerX) <= 0.037) return true;
        }
        return false;
    }

    public static boolean isPowerButton(BlockPos pos, Direction face, Vec3 hit) {
        Vec3 p = local(pos, hit);
        return face == Direction.NORTH && p.x >= 0.69 && p.x <= 0.96
                && p.y >= 0.24 && p.y <= 0.36;
    }

    private static Vec3 local(BlockPos pos, Vec3 hit) {
        return new Vec3(hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ());
    }

    public static void begin(Player player, Endpoint endpoint, BlockPos pos, CctvCableItem.Kind kind) {
        PENDING.put(player.getUUID(), new Pending(endpoint, pos.immutable(), kind));
        String msg = endpoint == Endpoint.MONITOR
                ? "[CCTV] HDMI: mufa monitor selectata. Acum apasa exact pe mufa HDMI din spatele XVR-ului."
                : "[CCTV] PoE: mufa camerei selectata. Acum apasa exact pe unul dintre cele 8 porturi RJ45 din fata XVR-ului.";
        player.displayClientMessage(Component.literal(msg), true);
    }

    public static boolean completePending(Player player, Level level, BlockPos xvrPos,
            Direction face, Vec3 hit, CctvCableItem.Kind heldKind) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            player.displayClientMessage(Component.literal("[CCTV] Incepe prin a da click exact pe mufa de pe monitor/camera cu cablul potrivit in mana."), true);
            return true;
        }
        if (pending.kind() != heldKind) {
            player.displayClientMessage(Component.literal("[CCTV] Tip de cablu gresit. Continua cu acelasi cablu cu care ai selectat prima mufa."), true);
            return true;
        }
        BlockEntity be = level.getBlockEntity(xvrPos);
        if (!(be instanceof XvrBlockEntity xvr)) return false;
        boolean correctPort = heldKind == CctvCableItem.Kind.HDMI
                ? isXvrHdmiPort(face, xvrPos, hit)
                : isXvrPoePort(face, xvrPos, hit);
        if (!correctPort) {
            player.displayClientMessage(Component.literal(heldKind == CctvCableItem.Kind.HDMI
                    ? "[CCTV] Mufa HDMI este pe panoul din SPATE al XVR-ului. Clic direct pe conector."
                    : "[CCTV] Clic direct pe unul dintre porturile RJ45 din FATA XVR-ului."), true);
            return true;
        }

        PENDING.remove(player.getUUID());
        if (pending.endpoint() == Endpoint.MONITOR && heldKind == CctvCableItem.Kind.HDMI) {
            if (!(level.getBlockEntity(pending.pos()) instanceof MonitorBlockEntity monitor)) {
                player.displayClientMessage(Component.literal("[CCTV] Monitorul selectat nu mai exista."), true);
                return true;
            }
            BlockPos oldXvrPos = monitor.getXvrPos();
            if (oldXvrPos != null && level.getBlockEntity(oldXvrPos) instanceof XvrBlockEntity oldXvr && !oldXvrPos.equals(xvrPos)) {
                oldXvr.setMonitorPos(null);
            }
            BlockPos oldMonitorPos = xvr.getMonitorPos();
            if (oldMonitorPos != null && level.getBlockEntity(oldMonitorPos) instanceof MonitorBlockEntity oldMonitor && !oldMonitorPos.equals(pending.pos())) {
                oldMonitor.setXvrPos(null);
            }
            xvr.setMonitorPos(pending.pos());
            monitor.setXvrPos(xvrPos);
            level.playSound(null, xvrPos, CctvSounds.get("hdmi_connect"), SoundSource.BLOCKS, 0.75F, 1.0F);
            player.displayClientMessage(Component.literal("[CCTV] HDMI conectat fizic intre cele doua mufe."), true);
            return true;
        }

        if (pending.endpoint() == Endpoint.CAMERA && heldKind == CctvCableItem.Kind.POE) {
            if (!(level.getBlockEntity(pending.pos()) instanceof CameraBlockEntity camera)) {
                player.displayClientMessage(Component.literal("[CCTV] Camera selectata nu mai exista."), true);
                return true;
            }
            BlockPos oldXvrPos = camera.getXvrPos();
            if (oldXvrPos != null && level.getBlockEntity(oldXvrPos) instanceof XvrBlockEntity oldXvr) {
                oldXvr.removeCamera(pending.pos());
            }
            xvr.addCamera(pending.pos());
            camera.setXvrPos(xvrPos);
            level.playSound(null, xvrPos, CctvSounds.randomVariant("camera_link_", 48), SoundSource.BLOCKS, 0.75F, 1.0F);
            player.displayClientMessage(Component.literal("[CCTV] Ethernet PoE Cat6 connected. Camera ID: " + camera.getCameraId()), false);
            player.displayClientMessage(Component.literal("[CCTV] Open the powered monitor to authenticate. Factory username: admin; factory password: 0000."), false);
            player.displayClientMessage(Component.literal("[CCTV] For a safer setup, change the factory password after the first successful login."), false);
            return true;
        }

        player.displayClientMessage(Component.literal("[CCTV] Conexiunea nu corespunde tipului de cablu."), true);
        return true;
    }

    public static void disconnectMonitor(Player player, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            BlockPos xvrPos = monitor.getXvrPos();
            if (xvrPos != null && level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr) xvr.setMonitorPos(null);
            monitor.setXvrPos(null);
            player.displayClientMessage(Component.literal("[CCTV] Cablul HDMI a fost deconectat."), true);
        }
    }

    public static void disconnectCamera(Player player, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
            BlockPos xvrPos = camera.getXvrPos();
            if (xvrPos != null && level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr) xvr.removeCamera(pos);
            camera.setXvrPos(null);
            player.displayClientMessage(Component.literal("[CCTV] Cablul PoE a fost deconectat."), true);
        }
    }

    public static void disconnectAll(Player player, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof XvrBlockEntity xvr)) return;
        BlockPos monitorPos = xvr.getMonitorPos();
        if (monitorPos != null && level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor) monitor.setXvrPos(null);
        xvr.setMonitorPos(null);
        for (BlockPos cameraPos : xvr.getCameras()) {
            if (level.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera) camera.setXvrPos(null);
        }
        for (BlockPos cameraPos : xvr.getCameras()) xvr.removeCamera(cameraPos);
        player.displayClientMessage(Component.literal("[CCTV] Toate conexiunile XVR au fost eliminate."), true);
    }
}

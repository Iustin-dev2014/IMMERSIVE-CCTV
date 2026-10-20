package ro.immersivecctv;

import java.util.function.Supplier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ro.immersivecctv.block.XvrBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Narrow server-authoritative channel for controls exposed by the XVR console. */
public final class CctvNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ImmersiveCCTV.MODID, "network"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;
    private record RemoteSession(ResourceLocation dimension, BlockPos xvrPos) {}
    private static final Map<UUID, RemoteSession> REMOTE_SESSIONS = new ConcurrentHashMap<>();

    private CctvNetwork() {}

    public static void register() {
        if (registered) return;
        CHANNEL.registerMessage(0, DeviceCommand.class, DeviceCommand::encode, DeviceCommand::decode, DeviceCommand::handle);
        CHANNEL.registerMessage(1, CloudLoginResult.class, CloudLoginResult::encode, CloudLoginResult::decode, CloudLoginResult::handle);
        CHANNEL.registerMessage(2, CloudEndpointLoginRequest.class, CloudEndpointLoginRequest::encode, CloudEndpointLoginRequest::decode, CloudEndpointLoginRequest::handle);
        CHANNEL.registerMessage(3, CloudScanRequest.class, CloudScanRequest::encode, CloudScanRequest::decode, CloudScanRequest::handle);
        CHANNEL.registerMessage(4, CloudScanResult.class, CloudScanResult::encode, CloudScanResult::decode, CloudScanResult::handle);
        registered = true;
    }

    public static void sendCommand(BlockPos xvrPos, BlockPos cameraPos, int action) {
        CHANNEL.sendToServer(new DeviceCommand(xvrPos, cameraPos, action, "", ""));
    }

    public static void sendXvrSetup(BlockPos xvrPos, String username, String password) {
        CHANNEL.sendToServer(new DeviceCommand(xvrPos, null, 10, username, password));
    }

    public static void sendCameraLogin(BlockPos xvrPos, BlockPos cameraPos, String username, String password) {
        CHANNEL.sendToServer(new DeviceCommand(xvrPos, cameraPos, 8, username, password));
    }

    public static void sendCameraPasswordChange(BlockPos xvrPos, BlockPos cameraPos, String newPassword) {
        CHANNEL.sendToServer(new DeviceCommand(xvrPos, cameraPos, 9, "", newPassword));
    }

    /** Starts a simulated cloud login. Server validates both endpoint availability and credentials. */
    public static void sendCloudLogin(BlockPos xvrPos, String username, String password) {
        CHANNEL.sendToServer(new DeviceCommand(xvrPos, null, 11, username, password));
    }

    public static void sendRemoteLogout(BlockPos xvrPos) {
        CHANNEL.sendToServer(new DeviceCommand(xvrPos, null, 12, "", ""));
    }

    /** Connect using a user-entered endpoint; XVR resolution and all checks happen on the server. */
    public static void sendCloudEndpointLogin(String host, String port, String username, String password) {
        CHANNEL.sendToServer(new CloudEndpointLoginRequest(host, port, username, password));
    }

    /** Scan currently loaded XVR endpoints within the explicitly requested 100-block radius. */
    public static void requestCloudScan() {
        CHANNEL.sendToServer(new CloudScanRequest());
    }

    private static boolean isRemoteSession(ServerPlayer player, BlockPos xvrPos) {
        RemoteSession session = REMOTE_SESSIONS.get(player.getUUID());
        return session != null && session.xvrPos().equals(xvrPos)
                && session.dimension().equals(player.serverLevel().dimension().location());
    }

    private static void sendCloudLoginResult(ServerPlayer player, boolean success, BlockPos pos, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CloudLoginResult(success, pos.immutable(), message));
    }


    private static boolean isOperationalConsole(ServerPlayer player, XvrBlockEntity xvr, BlockPos xvrPos) {
        var level = player.serverLevel();
        var state = level.getBlockState(xvrPos);
        if (!state.is(ImmersiveCCTV.XVR.get()) || !state.hasProperty(XvrBlock.POWERED)
                || !state.getValue(XvrBlock.POWERED) || !xvr.hasWorkingPower() || xvr.getMonitorPos() == null) {
            return false;
        }
        BlockEntity monitorEntity = level.getBlockEntity(xvr.getMonitorPos());
        return monitorEntity instanceof MonitorBlockEntity monitor
                && xvrPos.equals(monitor.getXvrPos()) && monitor.hasWorkingPower();
    }


    private static List<XvrBlockEntity> nearbyXvrs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int minChunkX = Math.floorDiv(center.getX() - 100, 16);
        int maxChunkX = Math.floorDiv(center.getX() + 100, 16);
        int minChunkZ = Math.floorDiv(center.getZ() - 100, 16);
        int maxChunkZ = Math.floorDiv(center.getZ() + 100, 16);
        List<XvrBlockEntity> found = new java.util.ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                var chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue; // Never force-load terrain merely to scan a remote.
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof XvrBlockEntity xvr) || !seen.add(xvr.getBlockPos())) continue;
                    if (xvr.getBlockPos().distToCenterSqr(player.getX(), player.getY(), player.getZ()) <= 100.0D * 100.0D) {
                        found.add(xvr);
                    }
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(x -> x.getBlockPos().distToCenterSqr(
                player.getX(), player.getY(), player.getZ())));
        return found;
    }

    private static XvrBlockEntity findEndpoint(ServerPlayer player, String host, String port) {
        for (XvrBlockEntity xvr : nearbyXvrs(player)) {
            if (xvr.matchesConfiguredEndpoint(host, port)) return xvr;
        }
        return null;
    }

    private static void handleCloudEndpointLogin(ServerPlayer player, String host, String port, String username, String password) {
        XvrBlockEntity xvr = findEndpoint(player, host, port);
        if (xvr == null) {
            sendCloudLoginResult(player, false, BlockPos.ZERO, "HOST UNREACHABLE");
            return;
        }
        BlockPos pos = xvr.getBlockPos();
        if (!xvr.canObtainCloudEndpoint()) {
            sendCloudLoginResult(player, false, pos, "HOST UNREACHABLE — the XVR must be powered and the router/WAN/fibre route must be online.");
            return;
        }
        if (!xvr.isAccountConfigured()) {
            sendCloudLoginResult(player, false, pos, "XVR account is not configured. Open the local monitor once to create the administrator account.");
            return;
        }
        if (!xvr.authenticateAccount(username, password)) {
            sendCloudLoginResult(player, false, pos, "Login failed: incorrect XVR username or password.");
            return;
        }
        REMOTE_SESSIONS.put(player.getUUID(), new RemoteSession(player.serverLevel().dimension().location(), pos.immutable()));
        sendCloudLoginResult(player, true, pos, "Cloud session authenticated.");
    }

    private record CloudEndpointLoginRequest(String host, String port, String username, String password) {
        private static void encode(CloudEndpointLoginRequest msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.host == null ? "" : msg.host.trim(), 64);
            buf.writeUtf(msg.port == null ? "" : msg.port.trim(), 8);
            buf.writeUtf(msg.username == null ? "" : msg.username.trim(), 64);
            buf.writeUtf(msg.password == null ? "" : msg.password, 128);
        }
        private static CloudEndpointLoginRequest decode(FriendlyByteBuf buf) {
            return new CloudEndpointLoginRequest(buf.readUtf(64), buf.readUtf(8), buf.readUtf(64), buf.readUtf(128));
        }
        private static void handle(CloudEndpointLoginRequest msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context ctx = contextSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) handleCloudEndpointLogin(player, msg.host, msg.port, msg.username, msg.password);
            });
            ctx.setPacketHandled(true);
        }
    }

    private record CloudScanRequest() {
        private static void encode(CloudScanRequest msg, FriendlyByteBuf buf) { }
        private static CloudScanRequest decode(FriendlyByteBuf buf) { return new CloudScanRequest(); }
        private static void handle(CloudScanRequest msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context ctx = contextSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                List<EndpointEntry> entries = new java.util.ArrayList<>();
                for (XvrBlockEntity xvr : nearbyXvrs(player)) {
                    boolean reachable = xvr.canObtainCloudEndpoint();
                    entries.add(new EndpointEntry(xvr.getBlockPos(), xvr.getConfiguredCloudIp(),
                            xvr.getConfiguredCloudPort(), reachable));
                    if (entries.size() >= 64) break;
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CloudScanResult(entries));
            });
            ctx.setPacketHandled(true);
        }
    }

    private record EndpointEntry(BlockPos pos, String host, String port, boolean reachable) { }

    private record CloudScanResult(List<EndpointEntry> entries) {
        private static void encode(CloudScanResult msg, FriendlyByteBuf buf) {
            int count = Math.min(64, msg.entries == null ? 0 : msg.entries.size());
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                EndpointEntry entry = msg.entries.get(i);
                buf.writeBlockPos(entry.pos());
                buf.writeUtf(entry.host(), 64);
                buf.writeUtf(entry.port(), 8);
                buf.writeBoolean(entry.reachable());
            }
        }
        private static CloudScanResult decode(FriendlyByteBuf buf) {
            int count = Math.max(0, Math.min(64, buf.readVarInt()));
            List<EndpointEntry> entries = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new EndpointEntry(buf.readBlockPos(), buf.readUtf(64), buf.readUtf(8), buf.readBoolean()));
            }
            return new CloudScanResult(entries);
        }
        private static void handle(CloudScanResult msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context ctx = contextSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ro.immersivecctv.client.ClientHooks.onCloudScanResult(msg.entries)));
            ctx.setPacketHandled(true);
        }
    }

    private record DeviceCommand(BlockPos xvrPos, BlockPos cameraPos, int action, String username, String password) {
        private static void encode(DeviceCommand msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.xvrPos);
            buf.writeBoolean(msg.cameraPos != null);
            if (msg.cameraPos != null) buf.writeBlockPos(msg.cameraPos);
            buf.writeVarInt(msg.action);
            buf.writeUtf(msg.username == null ? "" : msg.username, 64);
            buf.writeUtf(msg.password == null ? "" : msg.password, 128);
        }
        private static DeviceCommand decode(FriendlyByteBuf buf) {
            BlockPos xvr = buf.readBlockPos();
            BlockPos cam = buf.readBoolean() ? buf.readBlockPos() : null;
            int action = buf.readVarInt();
            String username = buf.readUtf(64);
            String password = buf.readUtf(128);
            return new DeviceCommand(xvr, cam, action, username, password);
        }
        private static void handle(DeviceCommand msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context ctx = contextSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !(player.serverLevel().getBlockEntity(msg.xvrPos) instanceof XvrBlockEntity xvr)) return;

                // Remote login is intentionally the only command accepted before a session exists.
                if (msg.action == 11) {
                    if (!xvr.isAccountConfigured()) {
                        sendCloudLoginResult(player, false, msg.xvrPos, "XVR account is not configured yet.");
                    } else if (!xvr.canObtainCloudEndpoint()) {
                        sendCloudLoginResult(player, false, msg.xvrPos, "Host unreachable. Check router power, WAN and fibre uplink.");
                    } else if (!xvr.authenticateAccount(msg.username, msg.password)) {
                        sendCloudLoginResult(player, false, msg.xvrPos, "Login failed: incorrect XVR username or password.");
                    } else {
                        REMOTE_SESSIONS.put(player.getUUID(), new RemoteSession(player.serverLevel().dimension().location(), msg.xvrPos.immutable()));
                        sendCloudLoginResult(player, true, msg.xvrPos, "Cloud session authenticated.");
                    }
                    return;
                }
                if (msg.action == 12) {
                    if (isRemoteSession(player, msg.xvrPos)) REMOTE_SESSIONS.remove(player.getUUID());
                    return;
                }

                boolean remoteSession = isRemoteSession(player, msg.xvrPos);
                if (remoteSession && !xvr.canObtainCloudEndpoint()) {
                    REMOTE_SESSIONS.remove(player.getUUID());
                    sendCloudLoginResult(player, false, msg.xvrPos, "Host unreachable. The cloud route was lost.");
                    return;
                }
                boolean remote = remoteSession;
                boolean closeEnough = player.distanceToSqr(msg.xvrPos.getX() + 0.5, msg.xvrPos.getY() + 0.5, msg.xvrPos.getZ() + 0.5) <= 64.0D;
                if (!closeEnough && !remote) return;
                if (msg.action == 10) {
                    if (!isOperationalConsole(player, xvr, msg.xvrPos)) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "[XVR] Initial setup requires a powered XVR and a connected, powered monitor."), false);
                        return;
                    }
                    boolean configured = xvr.configureAccount(msg.username, msg.password);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(configured
                            ? "[XVR] Contul initial a fost creat si salvat pentru acest recorder."
                            : "[XVR] Configurare respinsa. Contul exista deja sau datele sunt invalide."), false);
                    return;
                }
                // A remote operator may control cameras, but may not remotely power off the XVR.
                if (msg.action == 2) {
                    if (remote || !xvr.isAccountConfigured()) return;
                    var state = player.serverLevel().getBlockState(msg.xvrPos);
                    if (state.hasProperty(XvrBlock.POWERED) && state.getValue(XvrBlock.POWERED)) {
                        player.serverLevel().playSound(null, msg.xvrPos, CctvSounds.get("xvr_power_off"), SoundSource.BLOCKS, 0.82F, 0.82F);
                        player.serverLevel().setBlock(msg.xvrPos, state.setValue(XvrBlock.POWERED, false), 3);
                    }
                    return;
                }
                if (!xvr.isAccountConfigured() || (!remote && !isOperationalConsole(player, xvr, msg.xvrPos))) return;
                if (msg.cameraPos == null || !xvr.getCameras().contains(msg.cameraPos)
                        || !(player.serverLevel().getBlockEntity(msg.cameraPos) instanceof CameraBlockEntity camera)
                        || !msg.xvrPos.equals(camera.getXvrPos())) return;
                if (msg.action == 8) {
                    boolean accepted = camera.authenticate(msg.username, msg.password);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(accepted
                            ? (camera.mustChangePassword()
                                ? "[CCTV] Autentificare reusita. Schimba parola implicita inainte de a activa feed-ul."
                                : "[CCTV] Camera autentificata cu succes.")
                            : "[CCTV] Autentificare esuata: username sau parola incorecta."), false);
                    return;
                }
                if (msg.action == 9) {
                    boolean changed = camera.changePassword(msg.password);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(changed
                            ? "[CCTV] Parola camerei a fost schimbata. Feed-ul este autorizat."
                            : "[CCTV] Schimbare respinsa. Foloseste o parola de 4-64 caractere dupa autentificare."), false);
                    return;
                }
                if (msg.action >= 3 && msg.action <= 7) {
                    if (!camera.isAuthenticated() || camera.mustChangePassword() || !camera.isOnline() || !camera.isPtz()) return;
                    switch (msg.action) {
                        case 3 -> camera.adjustPtz(60.0F, 0.0F);
                        case 4 -> camera.adjustPtz(-60.0F, 0.0F);
                        case 5 -> camera.adjustPtz(0.0F, 35.0F);
                        case 6 -> camera.adjustPtz(0.0F, -35.0F);
                        case 7 -> camera.centerPtz();
                        default -> { }
                    }
                    return;
                }
                if (!camera.isAuthenticated() || camera.mustChangePassword()) return;
                if (msg.action == 0) {
                    camera.setEnabled(!camera.isEnabled());
                    player.serverLevel().playSound(null, msg.cameraPos,
                            CctvSounds.get(camera.isEnabled() ? "camera_enable" : "camera_disable"), SoundSource.BLOCKS, 0.7F, 1.0F);
                } else if (msg.action == 1 && camera.isOnline()) {
                    camera.restart();
                    player.serverLevel().playSound(null, msg.cameraPos, CctvSounds.get("camera_restart"), SoundSource.BLOCKS, 0.8F, 0.9F);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    private record CloudLoginResult(boolean success, BlockPos xvrPos, String message) {
        private static void encode(CloudLoginResult msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.success);
            buf.writeBlockPos(msg.xvrPos);
            buf.writeUtf(msg.message == null ? "" : msg.message, 256);
        }
        private static CloudLoginResult decode(FriendlyByteBuf buf) {
            return new CloudLoginResult(buf.readBoolean(), buf.readBlockPos(), buf.readUtf(256));
        }
        private static void handle(CloudLoginResult msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context ctx = contextSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ro.immersivecctv.client.ClientHooks.onCloudLoginResult(msg.success, msg.xvrPos, msg.message)));
            ctx.setPacketHandled(true);
        }
    }
}
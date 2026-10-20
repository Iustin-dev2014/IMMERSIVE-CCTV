package ro.immersivecctv.blockentity;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.block.RouterBlock;

/** Server-authoritative router state: 12V power, fibre uplink and four Ethernet LAN sockets. */
public final class RouterBlockEntity extends BlockEntity {
    private BlockPos powerOutletPos;
    private boolean adapterInstalled;
    private BlockPos internetBlockPos;
    private int fibrePort = -1;
    private final BlockPos[] lanXvrs = new BlockPos[4];
    private final long[] nextBlink = new long[4];
    private int blinkMask;
    private long nextRecheck;

    public RouterBlockEntity(BlockPos pos, BlockState state) { super(ImmersiveCCTV.ROUTER_BE.get(), pos, state); }
    public BlockPos getPowerOutletPos() { return powerOutletPos; }
    public BlockPos getInternetBlockPos() { return internetBlockPos; }
    public int getFibrePort() { return fibrePort; }
    public BlockPos getLanXvr(int port) { return port >= 0 && port < 4 ? lanXvrs[port] : null; }
    public boolean has12VAdapter() { return adapterInstalled; }
    public boolean hasWorkingPower() { return has12VAdapter() && level != null && powerOutletPos != null && level.getBlockState(powerOutletPos).is(ImmersiveCCTV.POWER_OUTLET.get()); }

    public boolean hasInternet() {
        if (level == null || internetBlockPos == null || fibrePort < 0) return false;
        if (!hasWorkingPower()) return false;
        if (!level.getBlockState(internetBlockPos).is(ImmersiveCCTV.INTERNET_BLOCK.get())) return false;
        return level.getBlockEntity(internetBlockPos) instanceof InternetBlockEntity internet
                && internet.isRouterConnected(fibrePort, worldPosition);
    }

    public void set12VAdapterInstalled(boolean value) { adapterInstalled = value; changedAndSync(); }
    public void setPowerOutletPos(BlockPos pos) { powerOutletPos = pos == null ? null : pos.immutable(); changedAndSync(); }

    public boolean connectFibre(BlockPos internetPos, int port) {
        if (level == null || level.isClientSide || port < 0 || port >= 5 || internetPos == null) return false;
        if (!(level.getBlockEntity(internetPos) instanceof InternetBlockEntity internet)) return false;
        if (internetBlockPos != null || !internet.connectRouter(port, worldPosition)) return false;
        internetBlockPos = internetPos.immutable(); fibrePort = port; changedAndSync(); refreshBlockState(); return true;
    }

    public void disconnectFibre() {
        if (level != null && internetBlockPos != null && fibrePort >= 0
                && level.getBlockEntity(internetBlockPos) instanceof InternetBlockEntity internet) {
            internet.disconnectRouter(fibrePort, worldPosition);
        }
        internetBlockPos = null; fibrePort = -1; changedAndSync(); refreshBlockState();
    }

    public boolean connectXvr(int port, BlockPos xvrPos) {
        if (level == null || level.isClientSide || port < 0 || port >= 4 || xvrPos == null) return false;
        if (!(level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr)) return false;
        if (lanXvrs[port] != null && !lanXvrs[port].equals(xvrPos)) return false;
        for (int i = 0; i < lanXvrs.length; i++) if (i != port && xvrPos.equals(lanXvrs[i])) return false;
        lanXvrs[port] = xvrPos.immutable();
        xvr.setRouterConnection(worldPosition, port);
        changedAndSync(); return true;
    }

    public void disconnectXvr(int port) {
        if (port < 0 || port >= 4) return;
        BlockPos old = lanXvrs[port]; lanXvrs[port] = null;
        if (level != null && old != null && level.getBlockEntity(old) instanceof XvrBlockEntity xvr
                && worldPosition.equals(xvr.getRouterPos())) xvr.setRouterConnection(null, -1);
        blinkMask &= ~(1 << port); changedAndSync(); refreshBlockState();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RouterBlockEntity router) {
        if (!(level instanceof ServerLevel server)) return;
        long now = level.getGameTime();
        if (now >= router.nextRecheck) {
            router.nextRecheck = now + 5;
            boolean hasInternet = router.hasInternet();
            BlockState current = level.getBlockState(pos);
            int validLanMask = 0;
            for (int i = 0; i < 4; i++) {
                BlockPos xvrPos = router.lanXvrs[i];
                boolean linked = xvrPos != null && level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr
                        && pos.equals(xvr.getRouterPos()) && xvr.getRouterLanPort() == i;
                if (!linked) {
                    router.lanXvrs[i] = null;
                    router.blinkMask &= ~(1 << i);
                    continue;
                }
                if (level.getBlockEntity(xvrPos) instanceof XvrBlockEntity linkedXvr) linkedXvr.refreshLinkIndicators();
                if (!current.getValue(RouterBlock.POWERED)) {
                    router.blinkMask &= ~(1 << i);
                } else if (now >= router.nextBlink[i]) {
                    if (server.getRandom().nextInt(4) == 0) router.blinkMask ^= (1 << i);
                    else router.blinkMask |= (1 << i);
                    router.nextBlink[i] = now + 3 + server.getRandom().nextInt(9);
                }
                if ((router.blinkMask & (1 << i)) != 0) validLanMask |= 1 << i;
            }
            BlockState updated = current;
            if (current.hasProperty(RouterBlock.INTERNET) && current.getValue(RouterBlock.INTERNET) != (current.getValue(RouterBlock.POWERED) && hasInternet)) {
                updated = updated.setValue(RouterBlock.INTERNET, current.getValue(RouterBlock.POWERED) && hasInternet);
            }
            if (updated.hasProperty(RouterBlock.LAN_MASK) && updated.getValue(RouterBlock.LAN_MASK) != validLanMask) updated = updated.setValue(RouterBlock.LAN_MASK, validLanMask);
            if (updated != current) level.setBlock(pos, updated, 3);
        }
    }

    private void refreshBlockState() {
        if (level == null || level.isClientSide || !level.getBlockState(worldPosition).hasProperty(RouterBlock.INTERNET)) return;
        BlockState state = level.getBlockState(worldPosition);
        boolean online = state.getValue(RouterBlock.POWERED) && hasInternet();
        if (state.getValue(RouterBlock.INTERNET) != online) level.setBlock(worldPosition, state.setValue(RouterBlock.INTERNET, online), 3);
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("AdapterInstalled", has12VAdapter());
        if (powerOutletPos != null) tag.put("PowerOutletPos", NbtUtils.writeBlockPos(powerOutletPos));
        if (internetBlockPos != null) tag.put("InternetBlockPos", NbtUtils.writeBlockPos(internetBlockPos));
        tag.putInt("FibrePort", fibrePort);
        for (int i = 0; i < 4; i++) if (lanXvrs[i] != null) tag.put("LanXvr" + i, NbtUtils.writeBlockPos(lanXvrs[i]));
        tag.putInt("BlinkMask", blinkMask);
        for (int i = 0; i < 4; i++) tag.putLong("NextBlink" + i, nextBlink[i]);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        adapterInstalled = tag.getBoolean("AdapterInstalled");
        powerOutletPos = tag.contains("PowerOutletPos") ? NbtUtils.readBlockPos(tag.getCompound("PowerOutletPos")) : null;
        internetBlockPos = tag.contains("InternetBlockPos") ? NbtUtils.readBlockPos(tag.getCompound("InternetBlockPos")) : null;
        fibrePort = tag.getInt("FibrePort");
        Arrays.fill(lanXvrs, null);
        for (int i = 0; i < 4; i++) if (tag.contains("LanXvr" + i)) lanXvrs[i] = NbtUtils.readBlockPos(tag.getCompound("LanXvr" + i));
        blinkMask = tag.getInt("BlinkMask");
        for (int i = 0; i < 4; i++) nextBlink[i] = tag.getLong("NextBlink" + i);
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AdapterInstalled", has12VAdapter());
        if (powerOutletPos != null) tag.put("PowerOutletPos", NbtUtils.writeBlockPos(powerOutletPos));
        if (internetBlockPos != null) tag.put("InternetBlockPos", NbtUtils.writeBlockPos(internetBlockPos));
        tag.putInt("FibrePort", fibrePort); tag.putInt("BlinkMask", blinkMask);
        for (int i = 0; i < 4; i++) if (lanXvrs[i] != null) tag.put("LanXvr" + i, NbtUtils.writeBlockPos(lanXvrs[i]));
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) { load(tag); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

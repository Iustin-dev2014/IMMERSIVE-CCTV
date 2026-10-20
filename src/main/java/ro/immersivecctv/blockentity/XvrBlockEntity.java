package ro.immersivecctv.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.SecurityHashes;

public class XvrBlockEntity extends BlockEntity {
    private BlockPos monitorPos;
    private BlockPos powerOutletPos;
    private BlockPos routerPos;
    private int routerLanPort = -1;
    private boolean adapterInstalled;
    private boolean accountConfigured;
    private String accountUsername = "";
    private String accountSalt = UUID.randomUUID().toString();
    private String networkAddressId = UUID.randomUUID().toString();
    private String accountPasswordHash = "";
    private final List<BlockPos> cameras = new ArrayList<>();

    public XvrBlockEntity(BlockPos pos, BlockState state) {
        super(ImmersiveCCTV.XVR_BE.get(), pos, state);
    }

    public BlockPos getMonitorPos() { return monitorPos; }
    public BlockPos getPowerOutletPos() { return powerOutletPos; }
    public BlockPos getRouterPos() { return routerPos; }
    public int getRouterLanPort() { return routerLanPort; }
    public boolean has12VAdapter() { return adapterInstalled; }
    public boolean isAccountConfigured() { return accountConfigured; }
    public String getAccountUsername() { return accountUsername; }

    /** Validates the first-boot XVR account without exposing its hash to the client. */
    public boolean authenticateAccount(String username, String password) {
        String candidate = username == null ? "" : username.trim();
        return level != null && !level.isClientSide && accountConfigured
                && candidate.equals(accountUsername) && password != null
                && SecurityHashes.matches(accountSalt, password, accountPasswordHash);
    }

    /** Returns an in-game simulated endpoint only when the complete power/WAN/fibre route is online. */
    public boolean canObtainCloudEndpoint() {
        if (level == null || !hasWorkingPower() || !hasWanLink()) return false;
        return routerPos != null && level.getBlockEntity(routerPos) instanceof RouterBlockEntity router && router.hasInternet();
    }

    /** Stable simulated address used by the remote terminal; it is not a public Internet address. */
    public String getConfiguredCloudIp() {
        UUID id = safeNetworkAddressId();
        long high = id.getMostSignificantBits();
        int a = (int) ((high >>> 40) & 255L);
        int b = (int) ((high >>> 32) & 255L);
        int c = (int) ((high >>> 24) & 255L);
        return "10." + a + "." + b + "." + c;
    }

    public String getConfiguredCloudPort() {
        UUID id = safeNetworkAddressId();
        long low = id.getLeastSignificantBits();
        int value = (int) Math.floorMod(low ^ (low >>> 32), 50000L);
        return Integer.toString(10000 + value);
    }

    /** Display address only when the endpoint is actually reachable right now. */
    public String getCloudIp() { return canObtainCloudEndpoint() ? getConfiguredCloudIp() : "Cannot obtain"; }
    public String getCloudPort() { return canObtainCloudEndpoint() ? getConfiguredCloudPort() : "Cannot obtain"; }

    /** Server-side lookup helper; callers must still verify canObtainCloudEndpoint before login. */
    public boolean matchesConfiguredEndpoint(String host, String port) {
        return host != null && port != null
                && getConfiguredCloudIp().equals(host.trim())
                && getConfiguredCloudPort().equals(port.trim());
    }

    private UUID safeNetworkAddressId() {
        try { return UUID.fromString(networkAddressId); }
        catch (IllegalArgumentException ex) {
            networkAddressId = UUID.randomUUID().toString();
            setChanged();
            return UUID.fromString(networkAddressId);
        }
    }

    /** The first console setup is permanent for this placed XVR. Breaking it removes this data. */
    public boolean configureAccount(String username, String password) {
        if (accountConfigured || level == null || level.isClientSide) return false;
        String user = username == null ? "" : username.trim();
        if (user.length() < 3 || user.length() > 32 || password == null || password.length() < 4 || password.length() > 64) return false;
        accountUsername = user;
        accountSalt = UUID.randomUUID().toString();
        accountPasswordHash = SecurityHashes.hash(accountSalt, password);
        accountConfigured = true;
        changedAndSync();
        return true;
    }
    public boolean hasWorkingPower() {
        return adapterInstalled && level != null && powerOutletPos != null
                && level.getBlockState(powerOutletPos).is(ImmersiveCCTV.POWER_OUTLET.get());
    }
    public void set12VAdapterInstalled(boolean installed) { adapterInstalled = installed; changedAndSync(); }
    public void setPowerOutletPos(BlockPos pos) { powerOutletPos = pos == null ? null : pos.immutable(); changedAndSync(); }
    public List<BlockPos> getCameras() { return List.copyOf(cameras); }

    public void setMonitorPos(BlockPos pos) {
        monitorPos = pos == null ? null : pos.immutable();
        changedAndSync();
    }

    public void setRouterConnection(BlockPos pos, int port) {
        routerPos = pos == null ? null : pos.immutable();
        routerLanPort = routerPos == null ? -1 : port;
        changedAndSync();
        refreshLinkIndicators();
    }

    public boolean hasWanLink() {
        return level != null && level.getBlockState(worldPosition).hasProperty(ro.immersivecctv.block.XvrBlock.POWERED)
                && level.getBlockState(worldPosition).getValue(ro.immersivecctv.block.XvrBlock.POWERED)
                && routerPos != null && routerLanPort >= 0
                && level.getBlockEntity(routerPos) instanceof RouterBlockEntity router
                && router.getLanXvr(routerLanPort) != null && worldPosition.equals(router.getLanXvr(routerLanPort))
                && level.getBlockState(routerPos).is(ImmersiveCCTV.ROUTER.get())
                && level.getBlockState(routerPos).getValue(ro.immersivecctv.block.RouterBlock.POWERED);
    }

    public void refreshLinkIndicators() {
        if (level == null || level.isClientSide || !level.getBlockState(worldPosition).is(ImmersiveCCTV.XVR.get())) return;
        BlockState state = level.getBlockState(worldPosition);
        boolean poe = !cameras.isEmpty();
        boolean wan = hasWanLink();
        if (state.getValue(ro.immersivecctv.block.XvrBlock.POE_LINK) != poe || state.getValue(ro.immersivecctv.block.XvrBlock.WAN_LINK) != wan) {
            level.setBlock(worldPosition, state.setValue(ro.immersivecctv.block.XvrBlock.POE_LINK, poe)
                    .setValue(ro.immersivecctv.block.XvrBlock.WAN_LINK, wan), 3);
        }
    }

    public void addCamera(BlockPos pos) {
        cameras.remove(pos);
        if (cameras.size() >= 8) cameras.remove(0);
        cameras.add(pos);
        changedAndSync();
        refreshLinkIndicators();
    }

    public void removeCamera(BlockPos pos) {
        if (cameras.remove(pos)) { changedAndSync(); refreshLinkIndicators(); }
    }


    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, XvrBlockEntity xvr) {
        if (!level.isClientSide && level.getGameTime() % 5L == 0L) xvr.refreshLinkIndicators();
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (monitorPos != null) tag.put("MonitorPos", NbtUtils.writeBlockPos(monitorPos));
        if (powerOutletPos != null) tag.put("PowerOutletPos", NbtUtils.writeBlockPos(powerOutletPos));
        if (routerPos != null) tag.put("RouterPos", NbtUtils.writeBlockPos(routerPos));
        tag.putInt("RouterLanPort", routerLanPort);
        tag.putBoolean("AdapterInstalled", adapterInstalled);
        tag.putBoolean("AccountConfigured", accountConfigured);
        tag.putString("AccountUsername", accountUsername);
        tag.putString("AccountSalt", accountSalt);
        tag.putString("NetworkAddressId", networkAddressId);
        tag.putString("AccountPasswordHash", accountPasswordHash);
        tag.putInt("CameraCount", cameras.size());
        for (int i = 0; i < cameras.size(); i++) tag.put("Camera" + i, NbtUtils.writeBlockPos(cameras.get(i)));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        monitorPos = tag.contains("MonitorPos") ? NbtUtils.readBlockPos(tag.getCompound("MonitorPos")) : null;
        routerPos = tag.contains("RouterPos") ? NbtUtils.readBlockPos(tag.getCompound("RouterPos")) : null;
        routerLanPort = tag.getInt("RouterLanPort");
        powerOutletPos = tag.contains("PowerOutletPos") ? NbtUtils.readBlockPos(tag.getCompound("PowerOutletPos")) : null;
        adapterInstalled = tag.getBoolean("AdapterInstalled");
        accountConfigured = tag.getBoolean("AccountConfigured");
        if (tag.contains("AccountUsername")) accountUsername = tag.getString("AccountUsername");
        // Client update tags intentionally omit the salt/hash. The server loads them from disk NBT.
        if (tag.contains("AccountSalt")) accountSalt = tag.getString("AccountSalt");
        if (tag.contains("NetworkAddressId")) networkAddressId = tag.getString("NetworkAddressId");
        if (tag.contains("AccountPasswordHash")) accountPasswordHash = tag.getString("AccountPasswordHash");
        cameras.clear();
        int count = Math.min(8, tag.getInt("CameraCount"));
        for (int i = 0; i < count; i++) {
            String key = "Camera" + i;
            if (tag.contains(key)) cameras.add(NbtUtils.readBlockPos(tag.getCompound(key)));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (monitorPos != null) tag.put("MonitorPos", NbtUtils.writeBlockPos(monitorPos));
        if (powerOutletPos != null) tag.put("PowerOutletPos", NbtUtils.writeBlockPos(powerOutletPos));
        if (routerPos != null) tag.put("RouterPos", NbtUtils.writeBlockPos(routerPos));
        tag.putInt("RouterLanPort", routerLanPort);
        tag.putBoolean("AdapterInstalled", adapterInstalled);
        tag.putBoolean("AccountConfigured", accountConfigured);
        tag.putString("AccountUsername", accountUsername);
        // The unique endpoint ID is public diagnostic data; syncing it keeps the client display
        // identical to the server-generated address without exposing password material.
        tag.putString("NetworkAddressId", networkAddressId);
        tag.putInt("CameraCount", cameras.size());
        for (int i = 0; i < cameras.size(); i++) tag.put("Camera" + i, NbtUtils.writeBlockPos(cameras.get(i)));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

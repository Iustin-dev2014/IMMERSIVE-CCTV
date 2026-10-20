package ro.immersivecctv.blockentity;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ro.immersivecctv.ImmersiveCCTV;

/** Five-port fibre distribution block. Each port can serve one physically linked router. */
public final class InternetBlockEntity extends BlockEntity {
    private final BlockPos[] routers = new BlockPos[5];
    public InternetBlockEntity(BlockPos pos, BlockState state) { super(ImmersiveCCTV.INTERNET_BE.get(), pos, state); }
    public BlockPos getRouter(int port) { return port >= 0 && port < 5 ? routers[port] : null; }
    public boolean isRouterConnected(int port, BlockPos router) { return port >= 0 && port < 5 && router != null && router.equals(routers[port]); }
    public int firstFreePort() { for (int i = 0; i < routers.length; i++) if (routers[i] == null) return i; return -1; }
    public boolean connectRouter(int port, BlockPos router) {
        if (level == null || level.isClientSide || port < 0 || port >= 5 || router == null) return false;
        if (routers[port] != null && !routers[port].equals(router)) return false;
        for (int i = 0; i < routers.length; i++) if (i != port && router.equals(routers[i])) return false;
        routers[port] = router.immutable(); changedAndSync(); return true;
    }
    public void disconnectRouter(int port, BlockPos router) {
        if (port < 0 || port >= 5 || routers[port] == null || !routers[port].equals(router)) return;
        routers[port] = null; changedAndSync();
    }
    private void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); for (int i = 0; i < 5; i++) if (routers[i] != null) tag.put("Router" + i, NbtUtils.writeBlockPos(routers[i])); }
    @Override public void load(CompoundTag tag) { super.load(tag); Arrays.fill(routers, null); for (int i = 0; i < 5; i++) if (tag.contains("Router" + i)) routers[i] = NbtUtils.readBlockPos(tag.getCompound("Router" + i)); }
    @Override public CompoundTag getUpdateTag() { CompoundTag tag = new CompoundTag(); for (int i = 0; i < 5; i++) if (routers[i] != null) tag.put("Router" + i, NbtUtils.writeBlockPos(routers[i])); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag) { load(tag); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

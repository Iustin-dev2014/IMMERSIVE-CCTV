package ro.immersivecctv.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ro.immersivecctv.ImmersiveCCTV;

public class MonitorBlockEntity extends BlockEntity {
    private BlockPos xvrPos;
    private BlockPos powerOutletPos;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ImmersiveCCTV.MONITOR_BE.get(), pos, state);
    }

    public BlockPos getXvrPos() { return xvrPos; }
    public BlockPos getPowerOutletPos() { return powerOutletPos; }
    public boolean hasWorkingPower() {
        return level != null && powerOutletPos != null
                && level.getBlockState(powerOutletPos).is(ImmersiveCCTV.POWER_OUTLET.get());
    }
    public void setPowerOutletPos(BlockPos pos) {
        powerOutletPos = pos == null ? null : pos.immutable();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setXvrPos(BlockPos pos) {
        xvrPos = pos;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (xvrPos != null) tag.put("XvrPos", NbtUtils.writeBlockPos(xvrPos));
        if (powerOutletPos != null) tag.put("PowerOutletPos", NbtUtils.writeBlockPos(powerOutletPos));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        xvrPos = tag.contains("XvrPos") ? NbtUtils.readBlockPos(tag.getCompound("XvrPos")) : null;
        powerOutletPos = tag.contains("PowerOutletPos") ? NbtUtils.readBlockPos(tag.getCompound("PowerOutletPos")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

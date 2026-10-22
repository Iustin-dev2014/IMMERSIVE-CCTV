package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MonitorBlockEntity extends BlockEntity {
    private BlockPos xvrPos = null;
    private boolean screenOn = true;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MONITOR_BE.get(), pos, state);
    }

    public BlockPos getXvrPos() {
        return xvrPos;
    }

    public boolean isScreenOn() {
        return screenOn;
    }

    public void setXvrPos(BlockPos pos) {
        this.xvrPos = pos;
        sync();
    }

    public void toggleScreen() {
        screenOn = !screenOn;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (xvrPos != null) {
            tag.putLong("Xvr", xvrPos.asLong());
        }
        tag.putBoolean("ScreenOn", screenOn);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        xvrPos = tag.contains("Xvr") ? BlockPos.of(tag.getLong("Xvr")) : null;
        screenOn = !tag.contains("ScreenOn") || tag.getBoolean("ScreenOn");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

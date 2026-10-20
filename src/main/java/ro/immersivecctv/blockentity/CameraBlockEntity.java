package ro.immersivecctv.blockentity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.SecurityHashes;

public class CameraBlockEntity extends BlockEntity {
    private BlockPos xvrPos;
    private boolean enabled = true;
    private long restartUntil = 0L;
    private float panDegrees = 0.0F;
    private float tiltDegrees = 0.0F;
    private String cameraId = UUID.randomUUID().toString();
    private String credentialUsername = "admin";
    private String credentialSalt = UUID.randomUUID().toString();
    private String credentialPasswordHash = SecurityHashes.hash(credentialSalt, "0000");
    private boolean passwordChanged;
    private boolean authenticated;
    private boolean mustChangePassword;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ImmersiveCCTV.CAMERA_BE.get(), pos, state);
    }

    public BlockPos getXvrPos() { return xvrPos; }
    public boolean isEnabled() { return enabled; }
    public boolean isRestarting() { return level != null && level.getGameTime() < restartUntil; }
    public boolean isOnline() { return enabled && !isRestarting(); }
    public boolean isPtz() { return level != null && level.getBlockState(worldPosition).is(ImmersiveCCTV.PTZ_CAMERA.get()); }
    public float getPanDegrees() { return panDegrees; }
    public float getTiltDegrees() { return tiltDegrees; }
    public String getCameraId() { return cameraId; }
    public String getCredentialUsername() { return credentialUsername; }
    public boolean hasChangedPassword() { return passwordChanged; }
    public boolean isAuthenticated() { return authenticated; }
    public boolean mustChangePassword() { return mustChangePassword; }

    /** Called on the logical server after a client submits its credentials. */
    public boolean authenticate(String username, String password) {
        if (level == null || level.isClientSide || username == null || password == null
                || !credentialUsername.equals(username.trim())
                || !SecurityHashes.matches(credentialSalt, password, credentialPasswordHash)) return false;
        authenticated = true;
        mustChangePassword = !passwordChanged;
        changedAndSync();
        return true;
    }

    public boolean changePassword(String password) {
        if (level == null || level.isClientSide || !authenticated || !mustChangePassword
                || password == null || password.length() < 4 || password.length() > 64) return false;
        credentialSalt = UUID.randomUUID().toString();
        credentialPasswordHash = SecurityHashes.hash(credentialSalt, password);
        passwordChanged = true;
        mustChangePassword = false;
        changedAndSync();
        return true;
    }
    public void adjustPtz(float panDelta, float tiltDelta) {
        if (!isPtz()) return;
        panDegrees = ((panDegrees + panDelta + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
        tiltDegrees = Math.max(-70.0F, Math.min(70.0F, tiltDegrees + tiltDelta));
        changedAndSync();
    }
    public void centerPtz() { if (isPtz()) { panDegrees = 0.0F; tiltDegrees = 0.0F; changedAndSync(); } }
    public void setEnabled(boolean value) { enabled = value; changedAndSync(); }
    public void restart() {
        if (level != null) restartUntil = level.getGameTime() + 60L;
        changedAndSync();
    }
    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setXvrPos(BlockPos pos) {
        BlockPos next = pos == null ? null : pos.immutable();
        if (xvrPos == null ? next != null : !xvrPos.equals(next)) {
            authenticated = false;
            mustChangePassword = false;
        }
        xvrPos = next;
        changedAndSync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (xvrPos != null) tag.put("XvrPos", NbtUtils.writeBlockPos(xvrPos));
        tag.putBoolean("CameraEnabled", enabled);
        tag.putLong("RestartUntil", restartUntil);
        tag.putFloat("PtzPan", panDegrees);
        tag.putFloat("PtzTilt", tiltDegrees);
        tag.putString("CameraId", cameraId);
        tag.putString("CredentialUsername", credentialUsername);
        tag.putString("CredentialSalt", credentialSalt);
        tag.putString("CredentialPasswordHash", credentialPasswordHash);
        tag.putBoolean("PasswordChanged", passwordChanged);
        tag.putBoolean("CameraAuthenticated", authenticated);
        tag.putBoolean("MustChangePassword", mustChangePassword);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        xvrPos = tag.contains("XvrPos") ? NbtUtils.readBlockPos(tag.getCompound("XvrPos")) : null;
        enabled = !tag.contains("CameraEnabled") || tag.getBoolean("CameraEnabled");
        restartUntil = tag.getLong("RestartUntil");
        panDegrees = tag.getFloat("PtzPan");
        tiltDegrees = tag.getFloat("PtzTilt");
        if (tag.contains("CameraId")) cameraId = tag.getString("CameraId");
        credentialUsername = tag.contains("CredentialUsername") ? tag.getString("CredentialUsername") : "admin";
        if (tag.contains("CredentialSalt")) credentialSalt = tag.getString("CredentialSalt");
        if (tag.contains("CredentialPasswordHash")) credentialPasswordHash = tag.getString("CredentialPasswordHash");
        passwordChanged = tag.getBoolean("PasswordChanged");
        authenticated = tag.getBoolean("CameraAuthenticated");
        mustChangePassword = tag.getBoolean("MustChangePassword");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (xvrPos != null) tag.put("XvrPos", NbtUtils.writeBlockPos(xvrPos));
        tag.putBoolean("CameraEnabled", enabled);
        tag.putLong("RestartUntil", restartUntil);
        tag.putFloat("PtzPan", panDegrees);
        tag.putFloat("PtzTilt", tiltDegrees);
        tag.putString("CameraId", cameraId);
        tag.putString("CredentialUsername", credentialUsername);
        tag.putBoolean("PasswordChanged", passwordChanged);
        tag.putBoolean("CameraAuthenticated", authenticated);
        tag.putBoolean("MustChangePassword", mustChangePassword);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

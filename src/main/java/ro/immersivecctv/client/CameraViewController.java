package ro.immersivecctv.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Temporarily makes Minecraft's own world renderer use a camera located at the CCTV lens.
 * This is deliberately not a low-resolution raycast preview: the normal block/entity renderer
 * renders the actual client world, using the player's normal graphics settings and framebuffer.
 */
public final class CameraViewController {
    private static Entity originalCamera;
    private static Entity viewEntity;
    private static Level viewLevel;
    private static CameraType originalCameraType;
    private static BlockPos targetPos;
    private static Direction targetFacing;
    private static float targetPan;
    private static float targetTilt;

    private CameraViewController() {}

    public static void show(BlockPos pos, Direction facing, float panDegrees, float tiltDegrees) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || pos == null) {
            restore();
            return;
        }
        targetPos = pos.immutable();
        targetFacing = facing == null ? Direction.NORTH : facing;
        targetPan = panDegrees;
        targetTilt = tiltDegrees;
    }

    /** Runs on the client tick so the active camera is set before the next world-render pass. */
    public static void tick() {
        if (targetPos == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || !(minecraft.screen instanceof XvrScreen)) {
            restore();
            return;
        }

        boolean firstActivation = originalCameraType == null;
        if (firstActivation) {
            Entity current = minecraft.getCameraEntity();
            originalCamera = current == null ? minecraft.player : current;
            originalCameraType = minecraft.options.getCameraType();
        } else if (viewLevel != null && viewLevel != level) {
            // Never try to restore an entity from a world that has already been unloaded.
            originalCamera = minecraft.player;
        }

        if (viewEntity == null || viewLevel != level || viewEntity.isRemoved()) {
            viewLevel = level;
            viewEntity = EntityType.ARMOR_STAND.create(level);
            if (viewEntity == null) {
                restore();
                return;
            }
            viewEntity.setInvisible(true);
            viewEntity.setNoGravity(true);
            viewEntity.setSilent(true);
            viewEntity.setInvulnerable(true);
            // Camera.setup consumes the entity directly, but registering it in ClientLevel also
            // keeps render/frustum lookups consistent on Forge clients and avoids a null-world view.
            if (!level.addFreshEntity(viewEntity)) {
                viewEntity = null;
                restore();
                return;
            }
        }

        // A third-person setting would place the camera behind the anchor and reveal the player.
        // Force first person only while the CCTV console is open; restore the user's setting later.
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (minecraft.getCameraEntity() != viewEntity) minecraft.setCameraEntity(viewEntity);

        double pan = Math.toRadians(targetPan);
        double tilt = Math.toRadians(targetTilt);
        double baseX = targetFacing.getStepX();
        double baseZ = targetFacing.getStepZ();
        double fx = baseX * Math.cos(pan) + baseZ * Math.sin(pan);
        double fz = -baseX * Math.sin(pan) + baseZ * Math.cos(pan);
        float yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
        float pitch = (float) -Math.toDegrees(tilt);

        // Position the virtual camera just in front of the lens, outside its own collision shape.
        double eyeX = targetPos.getX() + 0.5 + targetFacing.getStepX() * 0.52;
        double eyeY = targetPos.getY() + 0.61;
        double eyeZ = targetPos.getZ() + 0.5 + targetFacing.getStepZ() * 0.52;
        double entityY = eyeY - viewEntity.getEyeHeight();
        viewEntity.moveTo(eyeX, entityY, eyeZ, yaw, pitch);
        viewEntity.setYRot(yaw);
        viewEntity.setXRot(pitch);
    }

    public static void restore() {
        Minecraft minecraft = Minecraft.getInstance();
        if (viewEntity != null && minecraft.getCameraEntity() == viewEntity) {
            Entity restoreTo = originalCamera;
            if (restoreTo == null || restoreTo.isRemoved() || restoreTo.level() != minecraft.level) {
                restoreTo = minecraft.player;
            }
            if (restoreTo != null) minecraft.setCameraEntity(restoreTo);
        }
        if (viewEntity != null && !viewEntity.isRemoved()) viewEntity.discard();
        if (originalCameraType != null) minecraft.options.setCameraType(originalCameraType);
        targetPos = null;
        targetFacing = null;
        originalCamera = null;
        viewEntity = null;
        viewLevel = null;
        originalCameraType = null;
    }
}

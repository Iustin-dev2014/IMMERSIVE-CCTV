package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Positions of the sockets, in "model units" (0..16 per block) of a block that faces north.
 * Rear sockets live on the back face of each device.
 */
public final class Ports {
    private Ports() {
    }

    public static final double XVR_FRONT_Z = 2.0;
    public static final double XVR_REAR_Z = 14.0;
    public static final double XVR_BTN_X = 2.8;
    public static final double XVR_BTN_Y = 3.0;
    public static final double XVR_HDMI_X = 3.3;
    public static final double XVR_HDMI_Y = 3.9;

    public static final double MON_REAR_Z = 9.0;
    public static final double MON_HDMI_X = 11.5;
    public static final double MON_HDMI_Y = 7.0;
    public static final double MON_BTN_X = 1.5;
    public static final double MON_BTN_Y = 5.5;

    public static final double CAM_REAR_Z = 12.0;
    public static final double CAM_LAN_X = 8.0;
    public static final double CAM_LAN_Y = 6.8;

    public static double xvrLanX(int i) {
        return 5.6 + (i % 8) * 1.15;
    }

    public static double xvrLanY(int i) {
        return i < 8 ? 4.35 : 2.55;
    }

    /** Point inside the block (0..1 on each axis) -> model space of a block facing f (0..1). */
    public static Vec3 toLocal(Vec3 rel, Direction f) {
        double x = rel.x - 0.5;
        double z = rel.z - 0.5;
        double lx;
        double lz;
        switch (f) {
            case NORTH:
                lx = x;
                lz = z;
                break;
            case EAST:
                lx = z;
                lz = -x;
                break;
            case SOUTH:
                lx = -x;
                lz = -z;
                break;
            default:
                lx = -z;
                lz = x;
                break;
        }
        return new Vec3(lx + 0.5, rel.y, lz + 0.5);
    }

    /** Model space (0..1) -> point inside the block for a block facing f. */
    public static Vec3 toWorldRel(Vec3 local, Direction f) {
        double x = local.x - 0.5;
        double z = local.z - 0.5;
        double wx;
        double wz;
        switch (f) {
            case NORTH:
                wx = x;
                wz = z;
                break;
            case EAST:
                wx = -z;
                wz = x;
                break;
            case SOUTH:
                wx = -x;
                wz = -z;
                break;
            default:
                wx = z;
                wz = -x;
                break;
        }
        return new Vec3(wx + 0.5, local.y, wz + 0.5);
    }

    /** Model units -> absolute world position. */
    public static Vec3 world(BlockPos pos, Direction f, double lx, double ly, double lz) {
        Vec3 w = toWorldRel(new Vec3(lx / 16.0, ly / 16.0, lz / 16.0), f);
        return new Vec3(pos.getX() + w.x, pos.getY() + w.y, pos.getZ() + w.z);
    }

    /** Direction pointing out of the rear face of a block facing f. */
    public static Vec3 rearNormal(Direction f) {
        Direction r = f.getOpposite();
        return new Vec3(r.getStepX(), 0.0, r.getStepZ());
    }

    /** Absolute hit location -> model units (x, y, z in 0..16). */
    public static Vec3 hitLocal(Vec3 hit, BlockPos pos, Direction f) {
        Vec3 rel = new Vec3(hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ());
        return toLocal(rel, f).scale(16.0);
    }

    public static int xvrLanAt(Vec3 l) {
        for (int i = 0; i < XvrBlockEntity.CHANNELS; i++) {
            if (Math.abs(l.x - xvrLanX(i)) <= 0.6 && Math.abs(l.y - xvrLanY(i)) <= 0.9) {
                return i;
            }
        }
        return -1;
    }

    public static boolean xvrHdmiAt(Vec3 l) {
        return Math.abs(l.x - XVR_HDMI_X) <= 1.2 && Math.abs(l.y - XVR_HDMI_Y) <= 0.8;
    }

    public static boolean monHdmiAt(Vec3 l) {
        return Math.abs(l.x - MON_HDMI_X) <= 1.2 && Math.abs(l.y - MON_HDMI_Y) <= 0.8;
    }

    public static boolean camLanAt(Vec3 l) {
        return Math.abs(l.x - CAM_LAN_X) <= 1.0 && Math.abs(l.y - CAM_LAN_Y) <= 0.9;
    }

    public static boolean xvrButtonAt(Vec3 l) {
        return Math.hypot(l.x - XVR_BTN_X, l.y - XVR_BTN_Y) <= 1.2;
    }

    public static boolean monButtonAt(Vec3 l) {
        return Math.hypot(l.x - MON_BTN_X, l.y - MON_BTN_Y) <= 1.0;
    }
}

package com.immersivecctv;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the XVR status LEDs (front and LAN ports), plugs and hanging cables. */
public class XvrRenderer implements BlockEntityRenderer<XvrBlockEntity> {
    private static final int HDMI_COLOR = 0x1C1C1F;
    private static final int LAN_COLOR = 0x2A64D2;

    public XvrRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(XvrBlockEntity be) {
        return true;
    }

    @Override
    public void render(XvrBlockEntity be, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof XvrBlock)) {
            return;
        }
        Direction facing = state.getValue(XvrBlock.FACING);
        float t = level.getGameTime() + partial;
        if (be.isPowered()) {
            leds(be, facing, level, t, pose, buffers);
        }
        cables(be, facing, level, pose, buffers);
    }

    // ------------------------------------------------------------------ LEDs

    private void leds(XvrBlockEntity be, Direction facing, Level level, float t,
                      PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180F)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        VertexConsumer vc = buffers.getBuffer(RenderType.text(ScreenCanvas.WHITE));

        // front: red = power, blue = network (camera connected), green = HDD recording
        led(pose, vc, 5.6, 3.4, 1.985, 0.2, 0.2, 255, 50, 40);
        if (be.isNetworkUp()) {
            led(pose, vc, 4.9, 3.4, 1.985, 0.2, 0.2, 70, 150, 255);
        }
        if (be.isRecordingActive(level.getGameTime()) && hddBlink(t)) {
            led(pose, vc, 4.2, 3.4, 1.985, 0.2, 0.2, 60, 255, 95);
        }
        // back: every connected LAN port has a steady green and a flickering orange LED
        for (int i = 0; i < XvrBlockEntity.CHANNELS; i++) {
            if (be.isOnline(i)) {
                double cx = Ports.xvrLanX(i);
                double cy = Ports.xvrLanY(i);
                led(pose, vc, cx - 0.26, cy + 0.45, 14.02, 0.16, 0.13, 60, 255, 95);
                if (lanBlink(i, t)) {
                    led(pose, vc, cx + 0.26, cy + 0.45, 14.02, 0.16, 0.13, 255, 150, 30);
                }
            }
        }
        pose.popPose();
    }

    private static boolean hddBlink(float t) {
        int h = ((int) (t / 2F)) * 1103515245 + 12345;
        return ((h >>> 16) & 3) != 0;
    }

    private static boolean lanBlink(int port, float t) {
        int h = (((int) (t / 3F)) + port * 7919) * 1103515245 + 12345;
        return ((h >>> 16) & 3) < 2;
    }

    private static void led(PoseStack pose, VertexConsumer vc, double cx, double cy, double z,
                            double hw, double hh, int r, int g, int b) {
        quad(pose, vc, cx - hw * 2.6, cy - hh * 2.6, cx + hw * 2.6, cy + hh * 2.6, z, r, g, b, 70);
        quad(pose, vc, cx - hw, cy - hh, cx + hw, cy + hh, z, r, g, b, 255);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, double x0, double y0, double x1, double y1,
                             double z, int r, int g, int b, int a) {
        Matrix4f m = pose.last().pose();
        float fx0 = (float) (x0 / 16.0);
        float fy0 = (float) (y0 / 16.0);
        float fx1 = (float) (x1 / 16.0);
        float fy1 = (float) (y1 / 16.0);
        float fz = (float) (z / 16.0);
        v(vc, m, fx0, fy0, fz, r, g, b, a);
        v(vc, m, fx0, fy1, fz, r, g, b, a);
        v(vc, m, fx1, fy1, fz, r, g, b, a);
        v(vc, m, fx1, fy0, fz, r, g, b, a);
        v(vc, m, fx1, fy0, fz, r, g, b, a);
        v(vc, m, fx1, fy1, fz, r, g, b, a);
        v(vc, m, fx0, fy1, fz, r, g, b, a);
        v(vc, m, fx0, fy0, fz, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(0.5F, 0.5F).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    // ------------------------------------------------------------------ cables

    private void cables(XvrBlockEntity be, Direction facing, Level level, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ScreenCanvas.WHITE));
        BlockPos origin = be.getBlockPos();

        BlockPos mp = be.getMonitorPos();
        if (mp != null) {
            BlockState ms = level.getBlockState(mp);
            if (ms.getBlock() instanceof MonitorBlock) {
                Direction mf = ms.getValue(MonitorBlock.FACING);
                Vec3 a = Ports.world(origin, facing, Ports.XVR_HDMI_X, Ports.XVR_HDMI_Y, Ports.XVR_REAR_Z);
                Vec3 b = Ports.world(mp, mf, Ports.MON_HDMI_X, Ports.MON_HDMI_Y, Ports.MON_REAR_Z);
                connection(pose, vc, level, origin, a, Ports.rearNormal(facing), b, Ports.rearNormal(mf),
                        true, HDMI_COLOR);
            }
        }
        for (int i = 0; i < XvrBlockEntity.CHANNELS; i++) {
            BlockPos cp = be.getCamera(i);
            if (cp == null) {
                continue;
            }
            BlockState cs = level.getBlockState(cp);
            if (!(cs.getBlock() instanceof CameraBlock)) {
                continue;
            }
            Direction cf = cs.getValue(CameraBlock.FACING);
            Vec3 a = Ports.world(origin, facing, Ports.xvrLanX(i), Ports.xvrLanY(i), Ports.XVR_REAR_Z);
            Vec3 b = Ports.world(cp, cf, Ports.CAM_LAN_X, Ports.CAM_LAN_Y, Ports.CAM_REAR_Z);
            connection(pose, vc, level, origin, a, Ports.rearNormal(facing), b, Ports.rearNormal(cf),
                    false, LAN_COLOR);
        }
    }

    private void connection(PoseStack pose, VertexConsumer vc, Level level, BlockPos origin,
                            Vec3 a, Vec3 na, Vec3 b, Vec3 nb, boolean hdmi, int color) {
        double plugLen = 2.2 / 16.0;
        Vec3 aEnd = a.add(na.scale(plugLen));
        Vec3 bEnd = b.add(nb.scale(plugLen));
        plug(pose, vc, level, origin, a, na, hdmi, color);
        plug(pose, vc, level, origin, b, nb, hdmi, color);

        Vec3 o = new Vec3(origin.getX(), origin.getY(), origin.getZ());
        double dist = aEnd.distanceTo(bEnd);
        double k = Math.min(dist * 0.4, 1.4) + 0.12;
        Vec3 p1 = aEnd.add(na.scale(k));
        Vec3 p2 = bEnd.add(nb.scale(k));
        double droop = Math.min(dist * 0.10, 1.5) + 0.03;
        int n = Math.max(10, Math.min(40, (int) (dist * 3.0) + 8));
        double radius = hdmi ? 0.045 : 0.035;

        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double s = 1.0 - t;
            double x = s * s * s * aEnd.x + 3 * s * s * t * p1.x + 3 * s * t * t * p2.x + t * t * t * bEnd.x;
            double y = s * s * s * aEnd.y + 3 * s * s * t * p1.y + 3 * s * t * t * p2.y + t * t * t * bEnd.y;
            double z = s * s * s * aEnd.z + 3 * s * s * t * p1.z + 3 * s * t * t * p2.z + t * t * t * bEnd.z;
            y -= droop * 4.0 * t * (1.0 - t);
            pts[i] = new Vec3(x, y, z);
        }

        int sides = 6;
        Vec3[][] ring = new Vec3[n + 1][sides];
        Vec3[][] nrm = new Vec3[n + 1][sides];
        int[] lights = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            Vec3 tan = pts[Math.min(n, i + 1)].subtract(pts[Math.max(0, i - 1)]).normalize();
            Vec3 up = Math.abs(tan.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 side = tan.cross(up).normalize();
            Vec3 upv = side.cross(tan).normalize();
            for (int j = 0; j < sides; j++) {
                double ang = j * Math.PI * 2.0 / sides;
                Vec3 off = side.scale(Math.cos(ang)).add(upv.scale(Math.sin(ang)));
                ring[i][j] = pts[i].add(off.scale(radius)).subtract(o);
                nrm[i][j] = off;
            }
            lights[i] = LevelRenderer.getLightColor(level, BlockPos.containing(pts[i]));
        }
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int bl = color & 255;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < sides; j++) {
                int j2 = (j + 1) % sides;
                vtx(vc, m, nm, ring[i][j], nrm[i][j], r, g, bl, lights[i]);
                vtx(vc, m, nm, ring[i][j2], nrm[i][j2], r, g, bl, lights[i]);
                vtx(vc, m, nm, ring[i + 1][j2], nrm[i + 1][j2], r, g, bl, lights[i + 1]);
                vtx(vc, m, nm, ring[i + 1][j], nrm[i + 1][j], r, g, bl, lights[i + 1]);
            }
        }
    }

    private void plug(PoseStack pose, VertexConsumer vc, Level level, BlockPos origin, Vec3 base, Vec3 n,
                      boolean hdmi, int cableColor) {
        double u = 1.0 / 16.0;
        double w = hdmi ? 1.8 * u : 0.85 * u;
        double h = hdmi ? 0.65 * u : 1.0 * u;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(base.add(n.scale(0.1))));
        Vec3 o = new Vec3(origin.getX(), origin.getY(), origin.getZ());
        box(pose, vc, o, base, n, 0.0, 0.5 * u, w * 0.9, h * 0.9, 175, 177, 182, light);
        int br = hdmi ? 30 : 205;
        int bg = hdmi ? 30 : 210;
        int bb = hdmi ? 34 : 225;
        box(pose, vc, o, base, n, 0.5 * u, 1.5 * u, w, h, br, bg, bb, light);
        box(pose, vc, o, base, n, 1.5 * u, 2.2 * u, w * 0.7, h * 0.7,
                (cableColor >> 16) & 255, (cableColor >> 8) & 255, cableColor & 255, light);
    }

    private static void box(PoseStack pose, VertexConsumer vc, Vec3 origin, Vec3 base, Vec3 n,
                            double d0, double d1, double w, double h, int r, int g, int b, int light) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = n.cross(up).normalize();
        double hw = w / 2.0;
        double hh = h / 2.0;
        Vec3[] c = new Vec3[8];
        for (int di = 0; di < 2; di++) {
            double d = di == 0 ? d0 : d1;
            for (int si = 0; si < 2; si++) {
                for (int ui = 0; ui < 2; ui++) {
                    c[di * 4 + si * 2 + ui] = base.add(n.scale(d))
                            .add(side.scale(si == 0 ? -hw : hw))
                            .add(up.scale(ui == 0 ? -hh : hh))
                            .subtract(origin);
                }
            }
        }
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        face(vc, m, nm, c[4], c[5], c[7], c[6], n, r, g, b, light);
        face(vc, m, nm, c[0], c[2], c[3], c[1], n.scale(-1), r, g, b, light);
        face(vc, m, nm, c[2], c[3], c[7], c[6], side, r, g, b, light);
        face(vc, m, nm, c[0], c[4], c[5], c[1], side.scale(-1), r, g, b, light);
        face(vc, m, nm, c[1], c[3], c[7], c[5], up, r, g, b, light);
        face(vc, m, nm, c[0], c[4], c[6], c[2], up.scale(-1), r, g, b, light);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             Vec3 n, int r, int g, int b, int light) {
        vtx(vc, m, nm, p0, n, r, g, b, light);
        vtx(vc, m, nm, p1, n, r, g, b, light);
        vtx(vc, m, nm, p2, n, r, g, b, light);
        vtx(vc, m, nm, p3, n, r, g, b, light);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p, Vec3 n,
                            int r, int g, int b, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, 255)
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(nm, (float) n.x, (float) n.y, (float) n.z)
                .endVertex();
    }
}

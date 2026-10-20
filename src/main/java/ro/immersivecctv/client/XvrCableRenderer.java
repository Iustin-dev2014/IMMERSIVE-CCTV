package ro.immersivecctv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ro.immersivecctv.block.CameraBlock;
import ro.immersivecctv.block.PowerOutletBlock;
import ro.immersivecctv.block.XvrBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.RouterBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Draws thick, floor-routed cables and three functional front-panel status lamps. */
public class XvrCableRenderer implements BlockEntityRenderer<XvrBlockEntity> {
    public XvrCableRenderer(BlockEntityRendererProvider.Context context) {}

    // Forge 1.20.1's BlockEntityRenderer does not define getRenderBoundingBox.
    // The cable geometry extends beyond the XVR block, so permit off-screen rendering.
    @Override
    public boolean shouldRenderOffScreen(XvrBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(XvrBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;
        boolean powered = be.getBlockState().hasProperty(XvrBlock.POWERED) && be.getBlockState().getValue(XvrBlock.POWERED) && be.hasWorkingPower();
        boolean hasCamera = false;
        for (BlockPos cameraPos : be.getCameras()) {
            if (be.getLevel().getBlockEntity(cameraPos) instanceof CameraBlockEntity camera && be.getBlockPos().equals(camera.getXvrPos())) {
                hasCamera = true;
                break;
            }
        }

        ResourceLocation white = new ResourceLocation("immersivecctv", "textures/block/white.png");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(white));
        VertexConsumer ledVc = buffers.getBuffer(RenderType.entityTranslucent(white));
        PoseStack.Pose pose = poseStack.last();
        int fullBright = 0x00F000F0;
        drawLed(ledVc, pose, 0.525, 0.285, powered ? 0xFFFF302E : 0xFF351013, packedLight, packedOverlay, fullBright);
        drawLed(ledVc, pose, 0.585, 0.285, powered && hasCamera ? 0xFF259DFF : 0xFF0B1D2B, packedLight, packedOverlay, fullBright);
        boolean diskPulse = powered && be.getLevel().getGameTime() % 20 < 10;
        drawLed(ledVc, pose, 0.645, 0.285, diskPulse ? 0xFF3BFF8B : 0xFF123521, packedLight, packedOverlay, fullBright);

        if (be.getMonitorPos() != null && be.getLevel().getBlockEntity(be.getMonitorPos()) instanceof MonitorBlockEntity monitor
                && be.getBlockPos().equals(monitor.getXvrPos())) {
            BlockPos target = be.getMonitorPos();
            Vec3 sourcePort = new Vec3(0.5, 0.1875, 1.046875);
            Vec3 sourceDrop = new Vec3(0.5, 0.035, 1.15);
            Vec3 end = new Vec3(target.getX() - be.getBlockPos().getX() + 0.5,
                    target.getY() - be.getBlockPos().getY() + 0.375,
                    target.getZ() - be.getBlockPos().getZ() + 0.44);
            Vec3 endDrop = new Vec3(end.x, 0.035, end.z + 0.11);
            List<Vec3> points = floorRoute(sourcePort, sourceDrop, endDrop, end, true);
            drawTubePath(vc, pose, points, 0.033, 0xFF111519, 0xFF30383E, packedLight, packedOverlay);
            Vec3 outward = new Vec3(0, 0, 1);
            drawConnector(vc, pose, sourcePort, outward, true, packedLight, packedOverlay);
            drawConnector(vc, pose, end, outward, true, packedLight, packedOverlay);

            if (monitor.getPowerOutletPos() != null && be.getLevel().getBlockState(monitor.getPowerOutletPos()).is(ro.immersivecctv.ImmersiveCCTV.POWER_OUTLET.get())) {
                BlockPos outletPos = monitor.getPowerOutletPos();
                CablePort wallPort = outletPortRelative(be.getBlockPos(), outletPos, be.getLevel().getBlockState(outletPos).getValue(PowerOutletBlock.FACING));
                Vec3 monitorPower = new Vec3(target.getX() - be.getBlockPos().getX() + 0.5, target.getY() - be.getBlockPos().getY() + 0.375, target.getZ() - be.getBlockPos().getZ() + 1.035);
                Vec3 monitorPowerDrop = new Vec3(monitorPower.x, 0.035, monitorPower.z + 0.08);
                Vec3 wallDrop = new Vec3(wallPort.position().x, 0.035, wallPort.position().z);
                List<Vec3> powerRoute = floorRoute(monitorPower, monitorPowerDrop, wallDrop, wallPort.position(), false);
                drawTubePath(vc, pose, powerRoute, 0.022, 0xFF111416, 0xFFB99843, packedLight, packedOverlay);
                drawPowerPlug(vc, pose, monitorPower, new Vec3(0, 0, 1), packedLight, packedOverlay);
                drawPowerPlug(vc, pose, wallPort.position(), wallPort.outward(), packedLight, packedOverlay);
            }
        }

        if (be.getPowerOutletPos() != null && be.getLevel().getBlockState(be.getPowerOutletPos()).is(ro.immersivecctv.ImmersiveCCTV.POWER_OUTLET.get())) {
            BlockPos outletPos = be.getPowerOutletPos();
            CablePort wallPort = outletPortRelative(be.getBlockPos(), outletPos, be.getLevel().getBlockState(outletPos).getValue(PowerOutletBlock.FACING));
            Vec3 xvrPower = new Vec3(0.875, 0.20, 1.035);
            Vec3 xvrPowerDrop = new Vec3(xvrPower.x, 0.035, xvrPower.z + 0.07);
            Vec3 wallDrop = new Vec3(wallPort.position().x, 0.035, wallPort.position().z);
            List<Vec3> powerRoute = floorRoute(xvrPower, xvrPowerDrop, wallDrop, wallPort.position(), false);
            drawTubePath(vc, pose, powerRoute, 0.024, 0xFF101315, 0xFFC5A24D, packedLight, packedOverlay);
            drawPowerPlug(vc, pose, xvrPower, new Vec3(0, 0, 1), packedLight, packedOverlay);
            drawPowerPlug(vc, pose, wallPort.position(), wallPort.outward(), packedLight, packedOverlay);
        }

        // Visible Ethernet cable between the XVR LAN jack and the exact router LAN port.
        // Route down to the floor first, then across the floor, and finally up into the router.
        BlockPos routerPos = be.getRouterPos();
        int routerPort = be.getRouterLanPort();
        if (routerPos != null && routerPort >= 0 && routerPort < 4
                && be.getLevel().getBlockEntity(routerPos) instanceof RouterBlockEntity router
                && be.getBlockPos().equals(router.getLanXvr(routerPort))) {
            Vec3 lanPort = new Vec3(0.82, 0.18, -0.012);
            Vec3 lanDrop = new Vec3(lanPort.x, 0.035, -0.12);
            Vec3 routerPortPos = new Vec3(routerPos.getX() - be.getBlockPos().getX() + 0.5,
                    routerPos.getY() - be.getBlockPos().getY() + 0.20,
                    routerPos.getZ() - be.getBlockPos().getZ() + 0.985);
            Vec3 routerDrop = new Vec3(routerPortPos.x, 0.035, routerPortPos.z + 0.10);
            List<Vec3> lanRoute = floorRoute(lanPort, lanDrop, routerDrop, routerPortPos, false);
            drawTubePath(vc, pose, lanRoute, 0.030, 0xFF111820, 0xFF36A8D8, packedLight, packedOverlay);
            drawConnector(vc, pose, lanPort, new Vec3(0, 0, -1), false, packedLight, packedOverlay);
            drawConnector(vc, pose, routerPortPos, new Vec3(0, 0, 1), false, packedLight, packedOverlay);
        }

        int portIndex = 0;
        for (BlockPos cameraPos : be.getCameras()) {
            if (!(be.getLevel().getBlockEntity(cameraPos) instanceof CameraBlockEntity camera) || !be.getBlockPos().equals(camera.getXvrPos())) continue;
            Direction facing = be.getLevel().getBlockState(cameraPos).hasProperty(CameraBlock.FACING)
                    ? be.getLevel().getBlockState(cameraPos).getValue(CameraBlock.FACING) : Direction.NORTH;
            Vec3 end = cameraPortRelative(be.getBlockPos(), cameraPos, facing);
            double sourceX = 0.1309 + (portIndex % 8) * 0.0957;
            Vec3 sourcePort = new Vec3(sourceX, 0.171875, -0.01);
            Vec3 sourceDrop = new Vec3(sourceX, 0.035, -0.11);
            Vec3 rearDirection = new Vec3(-facing.getStepX(), 0, -facing.getStepZ());
            Vec3 endDrop = new Vec3(end.x + rearDirection.x * 0.11, 0.035, end.z + rearDirection.z * 0.11);
            List<Vec3> points = floorRoute(sourcePort, sourceDrop, endDrop, end, false);
            drawTubePath(vc, pose, points, 0.028, 0xFF07558E, 0xFF3298D8, packedLight, packedOverlay);
            drawConnector(vc, pose, sourcePort, new Vec3(0, 0, -1), false, packedLight, packedOverlay);
            drawConnector(vc, pose, end, rearDirection, false, packedLight, packedOverlay);
            portIndex++;
        }
    }

    private record CablePort(Vec3 position, Vec3 outward) {}

    private CablePort outletPortRelative(BlockPos origin, BlockPos outlet, Direction facing) {
        double x = outlet.getX() - origin.getX() + 0.5;
        double y = outlet.getY() - origin.getY() + 0.5;
        double z = outlet.getZ() - origin.getZ() + 0.5;
        return switch (facing) {
            case NORTH -> new CablePort(new Vec3(x, y, outlet.getZ() - origin.getZ() - 0.002), new Vec3(0, 0, -1));
            case SOUTH -> new CablePort(new Vec3(x, y, outlet.getZ() - origin.getZ() + 1.002), new Vec3(0, 0, 1));
            case WEST -> new CablePort(new Vec3(outlet.getX() - origin.getX() - 0.002, y, z), new Vec3(-1, 0, 0));
            case EAST -> new CablePort(new Vec3(outlet.getX() - origin.getX() + 1.002, y, z), new Vec3(1, 0, 0));
            default -> new CablePort(new Vec3(x, y, z), new Vec3(0, 0, -1));
        };
    }

    /** Moulded mains plug with a contrasting collar and contact pins. */
    private void drawPowerPlug(VertexConsumer vc, PoseStack.Pose pose, Vec3 port, Vec3 outward,
            int packedLight, int packedOverlay) {
        Vec3 axis = outward.normalize();
        Vec3 side = axis.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(axis).normalize();
        drawOrientedBox(vc, pose, port.add(axis.scale(0.005)), axis, side, up,
                0.064, 0.035, 0.032, 0xFF24292C, packedLight, packedOverlay);
        drawOrientedBox(vc, pose, port.add(axis.scale(0.010)), axis, side, up,
                0.014, 0.037, 0.034, 0xFFB49A58, packedLight, packedOverlay);
        for (int pin = -1; pin <= 1; pin += 2) {
            Vec3 center = port.add(axis.scale(0.070)).add(side.scale(pin * 0.014));
            drawOrientedBox(vc, pose, center, axis, side, up, 0.022, 0.0035, 0.0035,
                    0xFFE6C66E, packedLight, packedOverlay);
        }
    }

    private Vec3 cameraPortRelative(BlockPos origin, BlockPos target, Direction facing) {
        double x = target.getX() - origin.getX() + 0.5;
        double y = target.getY() - origin.getY() + 0.4375;
        double z = target.getZ() - origin.getZ() + 0.5;
        return switch (facing.getOpposite()) {
            case NORTH -> new Vec3(x, y, target.getZ() - origin.getZ() + 0.03125);
            case SOUTH -> new Vec3(x, y, target.getZ() - origin.getZ() + 0.96875);
            case WEST -> new Vec3(target.getX() - origin.getX() + 0.03125, y, z);
            case EAST -> new Vec3(target.getX() - origin.getX() + 0.96875, y, z);
            default -> new Vec3(x, y, z);
        };
    }

    private List<Vec3> floorRoute(Vec3 source, Vec3 sourceDrop, Vec3 targetDrop, Vec3 target, boolean hdmi) {
        List<Vec3> p = new ArrayList<>();
        p.add(source);
        p.add(sourceDrop);
        // Route along two right-angle legs on the floor, instead of a cable suspended in mid-air.
        p.add(new Vec3(targetDrop.x, sourceDrop.y, sourceDrop.z));
        p.add(new Vec3(targetDrop.x, sourceDrop.y, targetDrop.z));
        p.add(targetDrop);
        p.add(target);
        return p;
    }

    private void drawTubePath(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> points, double radius,
            int baseColor, int highlightColor, int packedLight, int packedOverlay) {
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i), b = points.get(i + 1);
            if (a.distanceToSqr(b) < 0.000001) continue;
            drawTubeSegment(vc, pose, a, b, radius, baseColor, highlightColor, packedLight, packedOverlay);
        }
    }

    private void drawTubeSegment(VertexConsumer vc, PoseStack.Pose pose, Vec3 start, Vec3 end, double radius,
            int baseColor, int highlightColor, int packedLight, int packedOverlay) {
        Vec3 axis = end.subtract(start).normalize();
        Vec3 reference = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = axis.cross(reference).normalize();
        Vec3 up = axis.cross(side).normalize();
        int sides = 7;
        for (int i = 0; i < sides; i++) {
            double a0 = 2 * Math.PI * i / sides;
            double a1 = 2 * Math.PI * (i + 1) / sides;
            Vec3 r0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 r1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            Vec3 p0 = start.add(r0.scale(radius));
            Vec3 p1 = end.add(r0.scale(radius));
            Vec3 p2 = end.add(r1.scale(radius));
            Vec3 p3 = start.add(r1.scale(radius));
            int color = (i == 1 || i == 2) ? highlightColor : baseColor;
            // Reverse winding so the outside of the cable tube remains visible with face culling.
            putVertex(vc, pose, p0, color, 0, 0, packedOverlay, packedLight, r0);
            putVertex(vc, pose, p3, color, 0, 1, packedOverlay, packedLight, r1);
            putVertex(vc, pose, p2, color, 1, 1, packedOverlay, packedLight, r1);
            putVertex(vc, pose, p1, color, 1, 0, packedOverlay, packedLight, r0);
        }
    }

    private void drawLed(VertexConsumer vc, PoseStack.Pose pose, double x, double y, int color,
            int packedLight, int packedOverlay, int fullBright) {
        // A soft halo, raised dark socket, metal rim, glass lens and specular glint read as hardware.
        drawDisc(vc, pose, x, y, 0.045, -0.010, withAlpha(color, 38), packedOverlay, fullBright);
        drawDisc(vc, pose, x, y, 0.029, -0.012, 0xFF0B0F12, packedOverlay, packedLight);
        drawDisc(vc, pose, x, y, 0.023, -0.015, 0xFF66727A, packedOverlay, packedLight);
        drawDisc(vc, pose, x, y, 0.018, -0.019, color, packedOverlay, fullBright);
        drawDisc(vc, pose, x - 0.005, y + 0.006, 0.0045, -0.021, 0xFFDDF8FF, packedOverlay, fullBright);
    }

    private int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | ((alpha & 255) << 24); }

    private void drawDisc(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double radius, double z,
            int color, int packedOverlay, int packedLight) {
        final int segments = 16;
        Vec3 normal = new Vec3(0, 0, -1);
        Vec3 center = new Vec3(x, y, z);
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;
            Vec3 p0 = new Vec3(x + Math.cos(a0) * radius, y + Math.sin(a0) * radius, z);
            Vec3 p1 = new Vec3(x + Math.cos(a1) * radius, y + Math.sin(a1) * radius, z);
            putVertex(vc, pose, center, color, 0.5F, 0.5F, packedOverlay, packedLight, normal);
            putVertex(vc, pose, p0, color, 0, 0, packedOverlay, packedLight, normal);
            putVertex(vc, pose, p1, color, 1, 1, packedOverlay, packedLight, normal);
        }
    }

    /** Draws a short, differently shaped male plug leaving the female socket, with a metal collar and contact strip. */
    private void drawConnector(VertexConsumer vc, PoseStack.Pose pose, Vec3 port, Vec3 outward, boolean hdmi,
            int packedLight, int packedOverlay) {
        Vec3 axis = outward.normalize();
        Vec3 side = axis.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(axis).normalize();
        double halfWidth = hdmi ? 0.061 : 0.045;
        double halfHeight = hdmi ? 0.034 : 0.030;
        double bodyLength = hdmi ? 0.088 : 0.078;
        int body = hdmi ? 0xFF252A2E : 0xFF9AC6D5;
        int collar = hdmi ? 0xFF808A91 : 0xFF327BA3;
        int dark = hdmi ? 0xFF111518 : 0xFF284653;
        Vec3 bodyStart = port.add(axis.scale(0.006));
        drawOrientedBox(vc, pose, bodyStart, axis, side, up, bodyLength, halfWidth, halfHeight,
                body, packedLight, packedOverlay);
        // Strain-relief boot at the cable end.
        drawOrientedBox(vc, pose, port.add(axis.scale(bodyLength - 0.006)), axis, side, up,
                0.026, halfWidth * 0.78, halfHeight * 0.82, dark, packedLight, packedOverlay);
        // Metallic/coloured nose ring just outside the connector socket.
        drawOrientedBox(vc, pose, port.add(axis.scale(0.012)), axis, side, up,
                0.012, halfWidth * 0.96, halfHeight * 0.92, collar, packedLight, packedOverlay);
        // Gold contact stripe on the upper side of the male plug, visible close to the socket.
        for (int pin = 0; pin < (hdmi ? 5 : 8); pin++) {
            double fraction = (pin + 0.5) / (hdmi ? 5.0 : 8.0);
            Vec3 center = port.add(axis.scale(0.027)).add(side.scale((fraction - 0.5) * halfWidth * 1.45))
                    .add(up.scale(halfHeight * 0.83));
            Vec3 a = center.add(side.scale(-0.0032)).add(axis.scale(-0.006));
            Vec3 b = center.add(side.scale(0.0032)).add(axis.scale(-0.006));
            Vec3 c = center.add(side.scale(0.0032)).add(axis.scale(0.006));
            Vec3 d = center.add(side.scale(-0.0032)).add(axis.scale(0.006));
            drawQuad(vc, pose, a, b, c, d, 0xFFE5B75D, up, packedLight, packedOverlay);
        }
    }

    private void drawOrientedBox(VertexConsumer vc, PoseStack.Pose pose, Vec3 start, Vec3 axis, Vec3 side, Vec3 up,
            double length, double halfWidth, double halfHeight, int color, int packedLight, int packedOverlay) {
        Vec3 a = start;
        Vec3 b = start.add(axis.scale(length));
        Vec3 aL = a.add(side.scale(-halfWidth)).add(up.scale(-halfHeight));
        Vec3 aR = a.add(side.scale(halfWidth)).add(up.scale(-halfHeight));
        Vec3 aRU = a.add(side.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 aLU = a.add(side.scale(-halfWidth)).add(up.scale(halfHeight));
        Vec3 bL = b.add(side.scale(-halfWidth)).add(up.scale(-halfHeight));
        Vec3 bR = b.add(side.scale(halfWidth)).add(up.scale(-halfHeight));
        Vec3 bRU = b.add(side.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 bLU = b.add(side.scale(-halfWidth)).add(up.scale(halfHeight));
        drawQuad(vc, pose, aLU, aRU, bRU, bLU, lighten(color, 1.22F), up, packedLight, packedOverlay);
        drawQuad(vc, pose, aR, aL, bL, bR, darken(color, 0.56F), up.scale(-1), packedLight, packedOverlay);
        drawQuad(vc, pose, aL, aLU, bLU, bL, darken(color, 0.72F), side.scale(-1), packedLight, packedOverlay);
        drawQuad(vc, pose, aRU, aR, bR, bRU, lighten(color, 1.08F), side, packedLight, packedOverlay);
        drawQuad(vc, pose, aLU, aL, aR, aRU, darken(color, 0.48F), axis.scale(-1), packedLight, packedOverlay);
        drawQuad(vc, pose, bL, bLU, bRU, bR, lighten(color, 1.02F), axis, packedLight, packedOverlay);
    }

    private void drawQuad(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            int color, Vec3 normal, int packedLight, int packedOverlay) {
        putVertex(vc, pose, a, color, 0, 0, packedOverlay, packedLight, normal);
        putVertex(vc, pose, b, color, 0, 1, packedOverlay, packedLight, normal);
        putVertex(vc, pose, c, color, 1, 1, packedOverlay, packedLight, normal);
        putVertex(vc, pose, d, color, 1, 0, packedOverlay, packedLight, normal);
    }

    private int lighten(int color, float factor) {
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return (a << 24) | (Math.min(255, (int)(r * factor)) << 16) | (Math.min(255, (int)(g * factor)) << 8) | Math.min(255, (int)(b * factor));
    }

    private int darken(int color, float factor) {
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return (a << 24) | ((int)(r * factor) << 16) | ((int)(g * factor) << 8) | (int)(b * factor);
    }

    private void putVertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int color, float u, float v,
            int packedOverlay, int packedLight, Vec3 normal) {
        int a = (color >>> 24) & 255, r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        vc.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a).uv(u, v).overlayCoords(packedOverlay).uv2(packedLight)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}

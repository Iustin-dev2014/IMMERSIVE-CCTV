package ro.immersivecctv.client;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.block.PowerOutletBlock;
import ro.immersivecctv.blockentity.InternetBlockEntity;
import ro.immersivecctv.blockentity.RouterBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Visible, thick router leads with floor-level runs and raised connector collars. */
public final class RouterCableRenderer implements BlockEntityRenderer<RouterBlockEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("immersivecctv", "textures/block/white.png");
    public RouterCableRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public boolean shouldRenderOffScreen(RouterBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 128; }

    @Override public void render(RouterBlockEntity router, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        if (router.getLevel() == null) return;
        var level = router.getLevel();
        BlockPos origin = router.getBlockPos();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose pose = stack.last();
        double[] lanX = {0.11, 0.30, 0.49, 0.68};

        for (int i = 0; i < 4; i++) {
            BlockPos xvrPos = router.getLanXvr(i);
            if (xvrPos == null || !(level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr)
                    || !origin.equals(xvr.getRouterPos()) || xvr.getRouterLanPort() != i) continue;
            Vec3 a = new Vec3(lanX[i], 0.325, 1.015);
            Vec3 b = new Vec3(xvrPos.getX() - origin.getX() + 0.5,
                    xvrPos.getY() - origin.getY() + 0.3125, xvrPos.getZ() - origin.getZ() + 1.035);
            drawPath(vc, pose, a, b, 0.032, 0xFF0D1821, 0xFF258BCC, light, overlay, 0.105);
            plug(vc, pose, a, new Vec3(0,0,1), light, overlay);
            plug(vc, pose, b, new Vec3(0,0,1), light, overlay);
        }

        BlockPos internetPos = router.getInternetBlockPos();
        int fibrePort = router.getFibrePort();
        if (internetPos != null && fibrePort >= 0
                && level.getBlockEntity(internetPos) instanceof InternetBlockEntity internet
                && internet.isRouterConnected(fibrePort, origin)) {
            double[] ports = {0.12, 0.31, 0.50, 0.69, 0.88};
            Vec3 a = new Vec3(0.88, 0.34, 1.018);
            Vec3 b = new Vec3(internetPos.getX() - origin.getX() + ports[fibrePort],
                    internetPos.getY() - origin.getY() + 0.3125, internetPos.getZ() - origin.getZ() + 0.50);
            drawPath(vc, pose, a, b, 0.024, 0xFF21150B, 0xFFFFBE55, light, overlay, 0.09);
            plug(vc, pose, a, new Vec3(0,0,1), light, overlay);
            plug(vc, pose, b, new Vec3(0,1,0), light, overlay);
        }

        BlockPos outlet = router.getPowerOutletPos();
        if (outlet != null && level.getBlockState(outlet).is(ImmersiveCCTV.POWER_OUTLET.get())) {
            Direction face = level.getBlockState(outlet).getValue(PowerOutletBlock.FACING);
            Vec3 a = new Vec3(0.84, 0.335, 1.025);
            Vec3 b = outletRelative(origin, outlet, face);
            drawPath(vc, pose, a, b, 0.028, 0xFF111416, 0xFFC5A24D, light, overlay, 0.08);
            plug(vc, pose, a, new Vec3(0,0,1), light, overlay);
            plug(vc, pose, b, new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()), light, overlay);
        }
    }

    private Vec3 outletRelative(BlockPos origin, BlockPos outlet, Direction facing) {
        double x=outlet.getX()-origin.getX()+0.5, y=outlet.getY()-origin.getY()+0.5, z=outlet.getZ()-origin.getZ()+0.5;
        return switch(facing) {
            case NORTH -> new Vec3(x,y,outlet.getZ()-origin.getZ()-0.002);
            case SOUTH -> new Vec3(x,y,outlet.getZ()-origin.getZ()+1.002);
            case WEST -> new Vec3(outlet.getX()-origin.getX()-0.002,y,z);
            case EAST -> new Vec3(outlet.getX()-origin.getX()+1.002,y,z);
            default -> new Vec3(x,y,z);
        };
    }

    private void drawPath(VertexConsumer vc, PoseStack.Pose pose, Vec3 start, Vec3 end, double radius,
            int base, int highlight, int light, int overlay, double slack) {
        Vec3 sd = new Vec3(start.x, 0.036, start.z + slack);
        Vec3 ed = new Vec3(end.x, 0.036, end.z);
        List<Vec3> p = new ArrayList<>();
        p.add(start); p.add(sd);
        p.add(new Vec3(ed.x, sd.y, sd.z));
        p.add(new Vec3(ed.x, sd.y, ed.z));
        p.add(ed); p.add(end);
        for (int i=0;i<p.size()-1;i++) if(p.get(i).distanceToSqr(p.get(i+1))>1E-6)
            tube(vc, pose, p.get(i), p.get(i+1), radius, base, highlight, light, overlay);
    }

    private void plug(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, Vec3 outward, int light, int overlay) {
        Vec3 axis=outward.normalize();
        tube(vc,pose,p,p.add(axis.scale(0.055)),0.044,0xFF151B20,0xFF65757F,light,overlay);
        tube(vc,pose,p.add(axis.scale(0.018)),p.add(axis.scale(0.038)),0.038,0xFF657078,0xFFC0C7CB,light,overlay);
    }

    private void tube(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, double radius,
            int base, int hi, int light, int overlay) {
        Vec3 axis=b.subtract(a).normalize();
        Vec3 ref=Math.abs(axis.y)<0.9?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 side=axis.cross(ref).normalize(), up=axis.cross(side).normalize();
        int n=8;
        for(int i=0;i<n;i++){
            double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n;
            Vec3 r0=side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 r1=side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            int color=(i==1||i==2)?hi:base;
            vertex(vc,pose,a.add(r0.scale(radius)),color,0,0,light,overlay,r0);
            vertex(vc,pose,a.add(r1.scale(radius)),color,0,1,light,overlay,r1);
            vertex(vc,pose,b.add(r1.scale(radius)),color,1,1,light,overlay,r1);
            vertex(vc,pose,b.add(r0.scale(radius)),color,1,0,light,overlay,r0);
        }
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int color, float u, float v,
            int light, int overlay, Vec3 normal) {
        vc.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z)
                .color((color>>>16)&255,(color>>>8)&255,color&255,(color>>>24)&255)
                .uv(u,v).overlayCoords(overlay).uv2(light)
                .normal(pose.normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
}
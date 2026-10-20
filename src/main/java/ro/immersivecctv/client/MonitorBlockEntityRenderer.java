package ro.immersivecctv.client;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import ro.immersivecctv.block.XvrBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

public class MonitorBlockEntityRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    public MonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MonitorBlockEntity monitor, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        boolean linked = false;
        boolean powered = false;
        XvrBlockEntity xvr = null;
        if (monitor.getLevel() != null && monitor.getXvrPos() != null
                && monitor.getLevel().getBlockEntity(monitor.getXvrPos()) instanceof XvrBlockEntity found
                && monitor.getBlockPos().equals(found.getMonitorPos())) {
            xvr = found;
            BlockState state = monitor.getLevel().getBlockState(monitor.getXvrPos());
            linked = true;
            powered = state.hasProperty(XvrBlock.POWERED) && state.getValue(XvrBlock.POWERED);
        }
        boolean hasCamera = false;
        BlockPos cameraPos = null;
        if (linked && xvr != null) {
            for (BlockPos candidate : xvr.getCameras()) {
                if (monitor.getLevel().getBlockEntity(candidate) instanceof CameraBlockEntity camera
                        && xvr.getBlockPos().equals(camera.getXvrPos())) {
                    hasCamera = true;
                    cameraPos = candidate;
                    break;
                }
            }
        }
        long ticks = monitor.getLevel() == null ? 0 : monitor.getLevel().getGameTime();
        boolean blink = ticks % 20 < 10;
        boolean monitorPower = monitor.hasWorkingPower();
        String line1 = linked && powered && monitorPower ? (blink ? "[REC] XVR LIVE" : "XVR LIVE") : "NO SIGNAL";
        String line2 = linked && powered && monitorPower ? (hasCamera ? "CAM 01 ACTIVE" : "NO POE CAMERA") : "CHECK HDMI / POWER";
        String line3 = cameraPos != null && linked && powered ? "XYZ " + cameraPos.getX() + " " + cameraPos.getY() + " " + cameraPos.getZ() : "IMMERSIVE CCTV";

        poseStack.pushPose();
        // The display surface faces NORTH; keep all text within its physical 12x8-pixel area.
        poseStack.translate(0.5D, 0.5D, 0.104D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        Font font = Minecraft.getInstance().font;
        int red = blink ? 0xFFFF3636 : 0xFF8B1A1A;
        drawFitted(font, line1, 0, -10, linked && powered ? (hasCamera && blink ? 0xFFFF3030 : 0xFF67F2D7) : red, poseStack, buffers);
        drawFitted(font, line2, 0, 0, linked && powered && hasCamera ? 0xFF8EF7B4 : (blink ? 0xFFD9B8B8 : 0xFF9B9B9B), poseStack, buffers);
        drawFitted(font, line3, 0, 10, linked && powered ? 0xFFB1EFF5 : 0xFF777777, poseStack, buffers);
        poseStack.popPose();
    }

    private void drawFitted(Font font, String text, int x, int y, int color, PoseStack poseStack, MultiBufferSource buffers) {
        float maxWorldWidth = 0.68F;
        float scale = Math.min(0.0065F, maxWorldWidth / Math.max(1, font.width(text)));
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, x - font.width(text) / 2.0F, y, color, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, 0x00F000F0);
        poseStack.popPose();
    }
}

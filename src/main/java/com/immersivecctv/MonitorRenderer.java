package com.immersivecctv;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Draws the picture on the screen of the monitor model. */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    private final Font font;

    public MonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(MonitorBlockEntity be, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (CameraFeeds.isRendering()) {
            return;
        }
        Level level = be.getLevel();
        BlockPos xvrPos = be.getXvrPos();
        if (level == null || xvrPos == null || !be.isScreenOn()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MonitorBlock)) {
            return;
        }

        float yRot = state.getValue(MonitorBlock.FACING).toYRot() + 180F;
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot));
        pose.translate(-0.5D, 0.0D, -0.5D);
        // top-left corner of the screen as seen by the viewer, just in front of the glass
        pose.translate(15.0D / 16.0D, 14.0D / 16.0D, 6.99D / 16.0D);
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        float u = 0.875F / XvrView.W;
        pose.scale(u, -u, u);

        LocalPlayer player = Minecraft.getInstance().player;
        boolean allowLive = player != null && player.distanceToSqr(
                be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) < 24.0 * 24.0;

        ScreenCanvas c = new ScreenCanvas(font);
        float t = level.getGameTime() + partial;
        XvrBlockEntity xvr = level.getBlockEntity(xvrPos) instanceof XvrBlockEntity x ? x : null;
        if (xvr == null || !xvr.isPowered()) {
            XvrView.noSignal(c);
        } else {
            XvrView.draw(c, xvr, level, t, -1, allowLive);
        }
        c.flush(pose, buffers);
        pose.popPose();
    }
}

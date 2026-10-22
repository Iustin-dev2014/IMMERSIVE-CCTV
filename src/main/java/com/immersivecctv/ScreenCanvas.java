package com.immersivecctv;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects rectangles, images and text in a virtual 160 x 90 screen and draws them
 * in the right order (backgrounds, images, overlays, text).
 */
public final class ScreenCanvas {
    public static final ResourceLocation WHITE =
            new ResourceLocation(ImmersiveCCTV.MODID, "textures/misc/white.png");
    public static final ResourceLocation NOISE =
            new ResourceLocation(ImmersiveCCTV.MODID, "textures/misc/noise.png");

    private record Quad(ResourceLocation tex, float x0, float y0, float x1, float y1, float z,
                        float u0, float v0, float u1, float v1, int argb) {
    }

    private record Txt(String s, float x, float y, float scale, int color) {
    }

    private final List<Quad> base = new ArrayList<>();
    private final List<Quad> images = new ArrayList<>();
    private final List<Quad> overlay = new ArrayList<>();
    private final List<Txt> texts = new ArrayList<>();
    private final Font font;

    public ScreenCanvas(Font font) {
        this.font = font;
    }

    public float width(String s) {
        return font.width(s);
    }

    /** z <= 0 means background layer, anything above is drawn after the images. */
    public void rect(float x0, float y0, float x1, float y1, float z, int argb) {
        Quad q = new Quad(WHITE, x0, y0, x1, y1, z, 0F, 0F, 1F, 1F, argb);
        if (z <= 0.0001F) {
            base.add(q);
        } else {
            overlay.add(q);
        }
    }

    public void image(ResourceLocation tex, float x0, float y0, float x1, float y1, float z,
                      float u0, float v0, float u1, float v1, int argb) {
        images.add(new Quad(tex, x0, y0, x1, y1, z, u0, v0, u1, v1, argb));
    }

    public void text(String s, float x, float y, float scale, int color) {
        texts.add(new Txt(s, x, y, scale, color));
    }

    public void textC(String s, float cx, float y, float scale, int color) {
        text(s, cx - font.width(s) * scale / 2F, y, scale, color);
    }

    public void flush(PoseStack pose, MultiBufferSource buffers) {
        emit(base, pose, buffers);
        emit(images, pose, buffers);
        emit(overlay, pose, buffers);
        for (Txt t : texts) {
            pose.pushPose();
            pose.translate(t.x(), t.y(), 1.0F);
            pose.scale(t.scale(), t.scale(), 1.0F);
            font.drawInBatch(t.s(), 0F, 0F, t.color(), false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        base.clear();
        images.clear();
        overlay.clear();
        texts.clear();
    }

    private static void emit(List<Quad> list, PoseStack pose, MultiBufferSource buffers) {
        if (list.isEmpty()) {
            return;
        }
        Matrix4f m = pose.last().pose();
        ResourceLocation cur = null;
        VertexConsumer vc = null;
        for (Quad q : list) {
            if (vc == null || !q.tex().equals(cur)) {
                vc = buffers.getBuffer(RenderType.text(q.tex()));
                cur = q.tex();
            }
            int a = q.argb() >>> 24;
            int r = (q.argb() >> 16) & 255;
            int g = (q.argb() >> 8) & 255;
            int b = q.argb() & 255;
            float z = q.z();
            // both windings so the quad is visible whatever the culling state is
            vert(vc, m, q.x0(), q.y0(), z, q.u0(), q.v0(), r, g, b, a);
            vert(vc, m, q.x0(), q.y1(), z, q.u0(), q.v1(), r, g, b, a);
            vert(vc, m, q.x1(), q.y1(), z, q.u1(), q.v1(), r, g, b, a);
            vert(vc, m, q.x1(), q.y0(), z, q.u1(), q.v0(), r, g, b, a);
            vert(vc, m, q.x1(), q.y0(), z, q.u1(), q.v0(), r, g, b, a);
            vert(vc, m, q.x1(), q.y1(), z, q.u1(), q.v1(), r, g, b, a);
            vert(vc, m, q.x0(), q.y1(), z, q.u0(), q.v1(), r, g, b, a);
            vert(vc, m, q.x0(), q.y0(), z, q.u0(), q.v0(), r, g, b, a);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                             int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}

package ro.immersivecctv.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import ro.immersivecctv.block.CameraBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;

/**
 * Compatibility renderer used when the native SecurityCraft-style camera viewpoint has no usable
 * framebuffer output. It keeps a textured, progressive block-raycast feed visible instead of a
 * completely black monitor. This is a fallback, not a substitute for off-screen world rendering.
 */
public final class CameraFallbackRenderer {
    private static final int FEED_W = 192;
    private static final int FEED_H = 108;
    private static final int RAYS_PER_FRAME = 900;
    private static final long FEED_REFRESH_MS = 650L;
    private static final double H_FOV_TAN = Math.tan(Math.toRadians(39.0));
    private static final double V_FOV_TAN = Math.tan(Math.toRadians(27.0));
    private static final ResourceLocation TEXTURE = new ResourceLocation("immersivecctv", "dynamic/cctv_fallback");

    private BlockPos cachedCameraPos;
    private int[] feedPixels = new int[0];
    private DynamicTexture feedTexture;
    private boolean buildingFeed;
    private boolean feedReady;
    private int feedCursor;
    private int activeFilter;
    private long nextFeedUpdate;
    private Level feedLevel;
    private Minecraft feedMinecraft;
    private Vec3 feedOrigin, feedForward, feedRight, feedUp;
    private Map<FaceKey, List<BakedQuad>> activeQuadCache = new HashMap<>();

    public void render(GuiGraphics g, BlockPos cameraPos, int filter, int x, int y, int w, int h) {
        activeFilter = filter;
        updateFeedPixels(cameraPos);
        drawCameraPixels(g, x, y, w, h);
    }

    public void close() {
        if (feedTexture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            feedTexture = null;
        }
        cachedCameraPos = null;
        feedReady = false;
        buildingFeed = false;
    }

    private Direction cameraFacing(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(pos).hasProperty(CameraBlock.FACING)) {
            return level.getBlockState(pos).getValue(CameraBlock.FACING);
        }
        return Direction.NORTH;
    }
    private Vec3 cameraOrigin(BlockPos pos) {
        Direction face = cameraFacing(pos);
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.61, pos.getZ() + 0.5)
                .add(new Vec3(face.getStepX(), 0, face.getStepZ()).scale(0.52));
    }
    private Vec3 forward(BlockPos pos) {
        Direction face = cameraFacing(pos);
        Vec3 base = new Vec3(face.getStepX(), 0, face.getStepZ());
        Level level = Minecraft.getInstance().level;
        CameraBlockEntity be = level != null && level.getBlockEntity(pos) instanceof CameraBlockEntity c ? c : null;
        double pan = be != null && be.isPtz() ? Math.toRadians(be.getPanDegrees()) : 0.0;
        double tilt = be != null && be.isPtz() ? Math.toRadians(be.getTiltDegrees()) : 0.0;
        double hx = base.x * Math.cos(pan) + base.z * Math.sin(pan);
        double hz = -base.x * Math.sin(pan) + base.z * Math.cos(pan);
        return new Vec3(hx * Math.cos(tilt), Math.sin(tilt), hz * Math.cos(tilt)).normalize();
    }
    private Vec3 right(BlockPos pos) {
        Vec3 f = forward(pos);
        return new Vec3(-f.z, 0, f.x).normalize();
    }

    private void updateFeedPixels(BlockPos cameraPos) {
        long now = System.currentTimeMillis();
        boolean cameraChanged = !cameraPos.equals(cachedCameraPos);
        if (cameraChanged || (!buildingFeed && now >= nextFeedUpdate)) {
            cachedCameraPos = cameraPos.immutable();
            feedPixels = new int[FEED_W * FEED_H];
            feedCursor = 0;
            feedMinecraft = Minecraft.getInstance();
            feedLevel = feedMinecraft.level;
            // Never expose a freshly zeroed texture while the raycaster is warming up.  Seed a
            // visible horizon first, upload it immediately, then replace it with the sampled
            // world progressively.  This also gives an intelligible preview if chunk data is
            // temporarily unavailable instead of a permanently black rectangle.
            seedPreviewPixels();
            if (feedMinecraft != null) {
                try {
                    uploadFeedTexture(feedMinecraft);
                    feedReady = true;
                } catch (RuntimeException ignored) {
                    feedReady = false;
                }
            }
            if (feedLevel == null) { buildingFeed = false; return; }
            feedOrigin = cameraOrigin(cameraPos);
            feedForward = forward(cameraPos);
            feedRight = right(cameraPos);
            feedUp = new Vec3(0, 1, 0);
            activeQuadCache = new HashMap<>();
            buildingFeed = true;
        }
        if (!buildingFeed) return;

        Minecraft mc = feedMinecraft;
        Level level = feedLevel;
        if (level == null || mc == null) { buildingFeed = false; return; }
        int endCursor = Math.min(feedPixels.length, feedCursor + RAYS_PER_FRAME);
        for (int index = feedCursor; index < endCursor; index++) {
                int py = index / FEED_W;
                int px = index % FEED_W;
                double h = (((px + 0.5) / FEED_W) * 2.0 - 1.0) * H_FOV_TAN;
                double v = (1.0 - ((py + 0.5) / FEED_H) * 2.0) * V_FOV_TAN;
                Vec3 direction = feedForward.add(feedRight.scale(h)).add(feedUp.scale(v)).normalize();
                Vec3 end = feedOrigin.add(direction.scale(28.0));
                BlockHitResult hit = level.clip(new ClipContext(feedOrigin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                int rgb;
                if (hit.getType() != HitResult.Type.MISS) {
                    BlockPos hitPos = hit.getBlockPos();
                    BlockState state = level.getBlockState(hitPos);
                    MapColor mapColor = state.getMapColor(level, hitPos);
                    int fallback = mapColor.col & 0xFFFFFF;
                    if (fallback == 0) fallback = state.getLightEmission() > 0 ? 0xE2A74F : 0x34383C;
                    rgb = sampleModelTexture(mc, level, state, hitPos, hit.getDirection(), hit.getLocation(), fallback, activeQuadCache);
                    int light = Math.max(level.getBrightness(LightLayer.BLOCK, hitPos), level.getBrightness(LightLayer.SKY, hitPos));
                    double distanceShade = Math.max(0.42, 1.0 - feedOrigin.distanceTo(hit.getLocation()) / 48.0);
                    double faceShade = switch (hit.getDirection().getAxis()) {
                        case Y -> hit.getDirection() == Direction.DOWN ? 0.55 : 1.0;
                        case X -> 0.72;
                        case Z -> 0.82;
                    };
                    double shade = Math.max(0.28, light / 15.0) * distanceShade * faceShade;
                    rgb = shade(rgb, shade);
                } else {
                    double t = py / (double) (FEED_H - 1);
                    rgb = t < 0.58 ? blend(0xFF1B2B45, 0xFF657B8B, t / 0.58) : blend(0xFF657B8B, 0xFF2B352A, (t - 0.58) / 0.42);
                    if (level.isThundering()) rgb = shade(rgb, 0.65);
                }
                feedPixels[index] = applyFilter(rgb);
        }
        feedCursor = endCursor;
        if (feedCursor >= feedPixels.length) {
            buildingFeed = false;
            feedReady = true;
            nextFeedUpdate = now + FEED_REFRESH_MS;
            uploadFeedTexture(mc);
        }
    }

    private void seedPreviewPixels() {
        for (int py = 0; py < FEED_H; py++) {
            double t = py / (double) Math.max(1, FEED_H - 1);
            int row = t < 0.61
                    ? blend(0xFF18283D, 0xFF718897, t / 0.61)
                    : blend(0xFF718897, 0xFF29352B, (t - 0.61) / 0.39);
            if (py > FEED_H * 0.57 && py < FEED_H * 0.62) row = blend(row, 0xFF9AABAD, 0.25);
            for (int px = 0; px < FEED_W; px++) {
                int value = row;
                if ((py % 4) == 0) value = shade(value, 0.96);
                feedPixels[py * FEED_W + px] = applyFilter(value);
            }
        }
    }

    private void uploadFeedTexture(Minecraft mc) {
        if (feedTexture == null) {
            feedTexture = new DynamicTexture(new NativeImage(FEED_W, FEED_H, false));
            mc.getTextureManager().register(TEXTURE, feedTexture);
        }
        NativeImage image = feedTexture.getPixels();
        if (image == null) return;
        for (int py = 0; py < FEED_H; py++) {
            for (int px = 0; px < FEED_W; px++) {
                int rgb = feedPixels[py * FEED_W + px];
                int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
                // NativeImage's packed pixel format is ABGR (R in the least-significant byte).
                image.setPixelRGBA(px, py, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
        feedTexture.upload();
    }

    private int sampleModelTexture(Minecraft mc, Level level, BlockState state, BlockPos pos,
            Direction face, Vec3 worldHit, int fallback, Map<FaceKey, List<BakedQuad>> quadCache) {
        try {
            FaceKey key = new FaceKey(state, face);
            List<BakedQuad> quads = quadCache.get(key);
            if (quads == null) {
                BakedModel model = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
                quads = model.getQuads(state, face, RandomSource.create(42L), ModelData.EMPTY, null);
                quadCache.put(key, quads);
            }
            double localX = worldHit.x - pos.getX();
            double localY = worldHit.y - pos.getY();
            double localZ = worldHit.z - pos.getZ();
            double[] target = projectFace(localX, localY, localZ, face.getAxis());

            for (BakedQuad quad : quads) {
                int[] packed = quad.getVertices();
                if (packed.length < 32) continue;
                UvVertex[] vertices = new UvVertex[4];
                for (int i = 0; i < 4; i++) {
                    int base = i * 8;
                    double x = Float.intBitsToFloat(packed[base]);
                    double y = Float.intBitsToFloat(packed[base + 1]);
                    double z = Float.intBitsToFloat(packed[base + 2]);
                    double[] projected = projectFace(x, y, z, face.getAxis());
                    vertices[i] = new UvVertex(projected[0], projected[1],
                            Float.intBitsToFloat(packed[base + 4]), Float.intBitsToFloat(packed[base + 5]));
                }
                float[] uv = interpolateUv(vertices[0], vertices[1], vertices[2], target[0], target[1]);
                if (uv == null) uv = interpolateUv(vertices[0], vertices[2], vertices[3], target[0], target[1]);
                if (uv == null) continue;

                TextureAtlasSprite sprite = quad.getSprite();
                NativeImage image = sprite.contents().getOriginalImage();
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) continue;
                double spriteWidth = sprite.getU1() - sprite.getU0();
                double spriteHeight = sprite.getV1() - sprite.getV0();
                if (spriteWidth <= 0.0 || spriteHeight <= 0.0) continue;
                int tx = clamp((int) Math.floor((uv[0] - sprite.getU0()) / spriteWidth * image.getWidth()), 0, image.getWidth() - 1);
                int ty = clamp((int) Math.floor((uv[1] - sprite.getV0()) / spriteHeight * image.getHeight()), 0, image.getHeight() - 1);
                // NativeImage stores pixels as ABGR; convert to the RGB integer used by the screen renderer.
                int pixel = image.getPixelRGBA(tx, ty);
                int alpha = (pixel >>> 24) & 255;
                if (alpha < 32) return fallback;
                int r = pixel & 255;
                int g = (pixel >>> 8) & 255;
                int b = (pixel >>> 16) & 255;
                if (quad.isTinted() && quad.getTintIndex() >= 0) {
                    int tint = mc.getBlockColors().getColor(state, level, pos, quad.getTintIndex());
                    if (tint >= 0) {
                        r = r * ((tint >> 16) & 255) / 255;
                        g = g * ((tint >> 8) & 255) / 255;
                        b = b * (tint & 255) / 255;
                    }
                }
                if (alpha < 255) {
                    double a = alpha / 255.0;
                    int fr = (fallback >> 16) & 255, fg = (fallback >> 8) & 255, fb = fallback & 255;
                    r = (int) (r * a + fr * (1 - a));
                    g = (int) (g * a + fg * (1 - a));
                    b = (int) (b * a + fb * (1 - a));
                }
                return (r << 16) | (g << 8) | b;
            }
        } catch (RuntimeException ignored) {
            // Resource packs and custom block models can expose unusual UV geometry; keep the feed alive.
        }
        return fallback;
    }

    private double[] projectFace(double x, double y, double z, Direction.Axis axis) {
        return switch (axis) {
            case X -> new double[] { z, y };
            case Y -> new double[] { x, z };
            case Z -> new double[] { x, y };
        };
    }

    private float[] interpolateUv(UvVertex p0, UvVertex p1, UvVertex p2, double x, double y) {
        double denominator = (p1.b() - p2.b()) * (p0.a() - p2.a())
                + (p2.a() - p1.a()) * (p0.b() - p2.b());
        if (Math.abs(denominator) < 1.0e-9) return null;
        double w0 = ((p1.b() - p2.b()) * (x - p2.a()) + (p2.a() - p1.a()) * (y - p2.b())) / denominator;
        double w1 = ((p2.b() - p0.b()) * (x - p2.a()) + (p0.a() - p2.a()) * (y - p2.b())) / denominator;
        double w2 = 1.0 - w0 - w1;
        if (w0 < -0.025 || w1 < -0.025 || w2 < -0.025 || w0 > 1.025 || w1 > 1.025 || w2 > 1.025) return null;
        float u = (float) (w0 * p0.u() + w1 * p1.u() + w2 * p2.u());
        float v = (float) (w0 * p0.v() + w1 * p1.v() + w2 * p2.v());
        return new float[] { u, v };
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private int applyFilter(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int lum = (r * 30 + g * 59 + b * 11) / 100;
        if (activeFilter == 1) return (Math.min(255, lum / 4) << 16) | (Math.min(255, lum + 24) << 8) | Math.min(255, lum / 3);
        if (activeFilter == 2) {
            if (lum < 55) return ((lum + 10) << 16) | (Math.max(0, lum / 3) << 8) | Math.min(255, 95 + lum);
            if (lum < 115) return (Math.min(255, lum * 2) << 16) | (Math.max(0, lum - 20) << 8) | 35;
            if (lum < 190) return (255 << 16) | (Math.min(255, lum + 20) << 8) | Math.max(0, lum / 4);
            return (255 << 16) | (255 << 8) | Math.min(255, lum + 20);
        }
        return rgb;
    }

    private int shade(int rgb, double factor) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r * factor) << 16) | ((int) (g * factor) << 8) | (int) (b * factor);
    }

    private int blend(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private record FaceKey(BlockState state, Direction face) {}
    private record UvVertex(double a, double b, float u, float v) {}

    private void drawCameraPixels(GuiGraphics g, int x, int y, int w, int h) {
        if (!feedReady || feedTexture == null || feedPixels.length != FEED_W * FEED_H) {
            // A graphical-resource failure should be visible as a no-signal slate, not a black
            // rectangle indistinguishable from the old broken video feed.
            int split = y + Math.max(1, (int) (h * 0.60));
            g.fillGradient(x, y, x + w, split, 0xFF18283D, 0xFF718897);
            g.fillGradient(x, split, x + w, y + h, 0xFF718897, 0xFF29352B);
            g.fill(x, split - 1, x + w, split + 1, 0xFF9AABAD);
            return;
        }
        // Use the source-size overload. Passing the screen width/height as UV pixel extents
        // caused the 192x108 image to tile across large displays (the repeated-camera bug).
        g.blit(TEXTURE, x, y, w, h, 0.0F, 0.0F, FEED_W, FEED_H, FEED_W, FEED_H);
    }
}

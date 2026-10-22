package com.immersivecctv;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Real live camera feeds: every requested camera renders the world from its own position
 * into an off-screen texture, a few times per second, between two normal frames.
 * The player never moves.
 */
@Mod.EventBusSubscriber(modid = ImmersiveCCTV.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraFeeds {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[] WIDTHS = {256, 384, 512};
    private static final int[] HEIGHTS = {144, 216, 288};
    private static final float PITCH = 12.0F;
    private static final float FOV = 72.0F;

    private static final Map<BlockPos, Feed> FEEDS = new HashMap<>();
    private static final CctvCamera CAMERA = new CctvCamera();
    private static boolean rendering = false;
    private static boolean failed = false;
    private static long lastGlobal = 0L;
    private static int counter = 0;

    private CameraFeeds() {
    }

    private static final class Feed {
        final BlockPos pos;
        TextureTarget target;
        ResourceLocation loc;
        int w;
        long lastRender = 0L;
        long lastRequest = 0L;
        boolean hasFrame = false;

        Feed(BlockPos pos) {
            this.pos = pos;
        }
    }

    /** Lets a feed texture (the colour buffer of a frame buffer) be used like a normal texture. */
    private static final class FeedTexture extends AbstractTexture {
        private final RenderTarget target;

        FeedTexture(RenderTarget target) {
            this.target = target;
        }

        @Override
        public void load(ResourceManager manager) throws IOException {
        }

        @Override
        public int getId() {
            return target.getColorTextureId();
        }

        @Override
        public void releaseId() {
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            super.setFilter(true, false);
        }
    }

    private static final class CctvCamera extends Camera {
        void aim(BlockGetter level, Entity entity, Vec3 pos, float yaw, float pitch) {
            this.setup(level, entity, true, false, 1.0F);
            this.setRotation(yaw, pitch);
            this.setPosition(pos.x, pos.y, pos.z);
        }
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static boolean isFailed() {
        return failed;
    }

    /** Called while drawing a monitor. Returns the texture of the latest frame, or null if none yet. */
    public static ResourceLocation request(BlockPos camPos) {
        if (failed || !ClientPrefs.liveRender) {
            return null;
        }
        Feed f = FEEDS.get(camPos);
        if (f == null) {
            if (FEEDS.size() >= 24) {
                return null;
            }
            f = new Feed(camPos.immutable());
            FEEDS.put(f.pos, f);
        }
        f.lastRequest = System.currentTimeMillis();
        return f.hasFrame && f.target != null ? f.loc : null;
    }

    private static void destroy(Minecraft mc, Feed f) {
        if (f.target != null) {
            mc.getTextureManager().release(f.loc);
            f.target.destroyBuffers();
            f.target = null;
        }
        f.hasFrame = false;
    }

    private static void ensureTarget(Minecraft mc, Feed f) {
        int q = ClientPrefs.quality;
        int w = WIDTHS[q];
        if (f.target != null && f.w == w) {
            return;
        }
        destroy(mc, f);
        f.w = w;
        f.target = new TextureTarget(w, HEIGHTS[q], true, Minecraft.ON_OSX);
        f.target.setFilterMode(9729);
        f.loc = new ResourceLocation(ImmersiveCCTV.MODID, "feed/" + (counter++));
        mc.getTextureManager().register(f.loc, new FeedTexture(f.target));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        for (Feed f : FEEDS.values()) {
            destroy(mc, f);
        }
        FEEDS.clear();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        Iterator<Feed> it = FEEDS.values().iterator();
        while (it.hasNext()) {
            Feed f = it.next();
            if (now - f.lastRequest > 3000L) {
                destroy(mc, f);
                it.remove();
            }
        }

        if (failed || !ClientPrefs.liveRender || mc.level == null || mc.player == null
                || Minecraft.useShaderTransparency()) {
            return;
        }
        if (now - lastGlobal < 1000L / Math.max(1, ClientPrefs.fps())) {
            return;
        }
        Feed best = null;
        for (Feed f : FEEDS.values()) {
            if (now - f.lastRequest <= 700L && (best == null || f.lastRender < best.lastRender)) {
                best = f;
            }
        }
        if (best == null) {
            return;
        }
        best.lastRender = now;
        lastGlobal = now;

        BlockState st = mc.level.getBlockState(best.pos);
        if (!(st.getBlock() instanceof CameraBlock) || !mc.level.hasChunkAt(best.pos)) {
            return;
        }
        try {
            ensureTarget(mc, best);
            rendering = true;
            renderFeed(mc, best, st.getValue(CameraBlock.FACING), event.renderTickTime);
            best.hasFrame = true;
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("Immersive CCTV: live camera rendering failed, falling back to the simulated picture", t);
        } finally {
            rendering = false;
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void renderFeed(Minecraft mc, Feed feed, Direction facing, float partial) {
        BlockPos p = feed.pos;
        Vec3 camPos = new Vec3(p.getX() + 0.5 + facing.getStepX() * 0.47,
                p.getY() + 7.0 / 16.0,
                p.getZ() + 0.5 + facing.getStepZ() * 0.47);
        CAMERA.aim(mc.level, mc.player, camPos, facing.toYRot(), PITCH);

        feed.target.setClearColor(0.05F, 0.05F, 0.06F, 1.0F);
        feed.target.clear(Minecraft.ON_OSX);
        feed.target.bindWrite(true);

        float aspect = (float) feed.target.width / (float) feed.target.height;
        Matrix4f projection = new Matrix4f().setPerspective(
                (float) Math.toRadians(FOV), aspect, 0.05F, mc.gameRenderer.getRenderDistance() * 4.0F);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(CAMERA.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees(CAMERA.getYRot() + 180.0F));
        Matrix3f inverse = new Matrix3f(pose.last().normal()).invert();
        RenderSystem.setInverseViewRotationMatrix(inverse);

        mc.levelRenderer.prepareCullFrustum(pose, CAMERA.getPosition(), projection);
        mc.levelRenderer.renderLevel(pose, partial, Util.getNanos() + 2_000_000L, false, CAMERA,
                mc.gameRenderer, mc.gameRenderer.lightTexture(), projection);
    }
}

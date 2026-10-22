package ro.immersivecctv.client;

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
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.block.CameraBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Optional off-screen CCTV renderer. It renders a camera's world view into a private texture
 * without moving the player's camera, and restores the renderer matrices/target after each pass.
 * The existing raycast renderer remains the fallback if the off-screen pipeline is unavailable.
 */
@Mod.EventBusSubscriber(modid = ImmersiveCCTV.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraFeeds {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WIDTH = 384;
    private static final int HEIGHT = 216;
    private static final long MIN_INTERVAL_MS = 100L; // maximum 10 fps across all feeds
    private static final float FOV = 72.0F;
    private static final Map<BlockPos, Feed> FEEDS = new HashMap<>();
    private static final CctvCamera CAMERA = new CctvCamera();
    private static boolean rendering;
    private static boolean disabled;
    private static long lastGlobalRender;
    private static int textureCounter;

    private CameraFeeds() {}

    private static final class Feed {
        final BlockPos pos;
        TextureTarget target;
        ResourceLocation location;
        long lastRender;
        long lastRequest;
        int failures;
        boolean hasFrame;

        Feed(BlockPos pos) { this.pos = pos.immutable(); }
    }

    private static final class FeedTexture extends AbstractTexture {
        private final RenderTarget target;

        FeedTexture(RenderTarget target) { this.target = target; }

        @Override public void load(ResourceManager manager) throws IOException {}
        @Override public int getId() { return target.getColorTextureId(); }
        @Override public void releaseId() {}
        @Override public void setFilter(boolean blur, boolean mipmap) { super.setFilter(true, false); }
    }

    private static final class CctvCamera extends Camera {
        void aim(BlockGetter level, Entity entity, Vec3 position, float yaw, float pitch) {
            setup(level, entity, true, false, 1.0F);
            setRotation(yaw, pitch);
            setPosition(position.x, position.y, position.z);
        }
    }

    public static boolean isRendering() { return rendering; }
    public static boolean isDisabled() { return disabled; }

    /** Requests the newest camera frame; null means the caller should draw its fallback feed. */
    public static ResourceLocation request(BlockPos cameraPos) {
        if (disabled || Minecraft.getInstance().level == null) return null;
        Feed feed = FEEDS.get(cameraPos);
        if (feed == null) {
            if (FEEDS.size() >= 24) return null;
            feed = new Feed(cameraPos);
            FEEDS.put(feed.pos, feed);
        }
        feed.lastRequest = System.currentTimeMillis();
        return feed.hasFrame && feed.target != null ? feed.location : null;
    }

    private static void destroy(Minecraft mc, Feed feed) {
        if (feed.target != null) {
            if (feed.location != null) mc.getTextureManager().release(feed.location);
            feed.target.destroyBuffers();
            feed.target = null;
        }
        feed.hasFrame = false;
        feed.location = null;
    }

    private static void ensureTarget(Minecraft mc, Feed feed) {
        if (feed.target != null && feed.target.width == WIDTH && feed.target.height == HEIGHT) return;
        destroy(mc, feed);
        feed.target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        feed.target.setFilterMode(9729);
        feed.location = new ResourceLocation(ImmersiveCCTV.MODID, "feed/offscreen_" + textureCounter++);
        mc.getTextureManager().register(feed.location, new FeedTexture(feed.target));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        for (Feed feed : FEEDS.values()) destroy(mc, feed);
        FEEDS.clear();
        disabled = false;
        lastGlobalRender = 0L;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || disabled || rendering) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || Minecraft.useShaderTransparency()) return;
        long now = System.currentTimeMillis();

        Iterator<Feed> iterator = FEEDS.values().iterator();
        while (iterator.hasNext()) {
            Feed feed = iterator.next();
            if (now - feed.lastRequest > 3000L) {
                destroy(mc, feed);
                iterator.remove();
            }
        }
        if (now - lastGlobalRender < MIN_INTERVAL_MS) return;

        Feed selected = null;
        for (Feed feed : FEEDS.values()) {
            if (now - feed.lastRequest <= 700L && (selected == null || feed.lastRender < selected.lastRender)) {
                selected = feed;
            }
        }
        if (selected == null) return;
        selected.lastRender = now;
        lastGlobalRender = now;

        BlockState state = mc.level.getBlockState(selected.pos);
        if (!(state.getBlock() instanceof CameraBlock) || !mc.level.hasChunkAt(selected.pos)) return;

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix3f previousInverseView = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        RenderTarget mainTarget = mc.getMainRenderTarget();
        try {
            ensureTarget(mc, selected);
            rendering = true;
            renderFeed(mc, selected, state.getValue(CameraBlock.FACING), event.renderTickTime);
            selected.hasFrame = true;
            selected.failures = 0;
        } catch (Throwable failure) {
            selected.failures++;
            if (selected.failures == 1) {
                LOGGER.error("IMMERSIVE CCTV off-screen feed failed; using the fallback camera image.", failure);
            }
            if (selected.failures >= 3) {
                disabled = true;
                LOGGER.error("IMMERSIVE CCTV disabled off-screen rendering after repeated failures.");
            }
        } finally {
            // renderLevel mutates global render state. Restore it even if rendering throws.
            try {
                RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.setInverseViewRotationMatrix(previousInverseView);
            } catch (Throwable restoreFailure) {
                disabled = true;
                LOGGER.error("IMMERSIVE CCTV could not restore Minecraft render state; off-screen rendering disabled.", restoreFailure);
            }
            rendering = false;
            mainTarget.bindWrite(true);
        }
    }

    private static void renderFeed(Minecraft mc, Feed feed, Direction facing, float partialTick) {
        BlockPos pos = feed.pos;
        CameraBlockEntity cameraEntity = mc.level.getBlockEntity(pos) instanceof CameraBlockEntity be ? be : null;
        float pan = cameraEntity != null && cameraEntity.isPtz() ? cameraEntity.getPanDegrees() : 0.0F;
        float tilt = cameraEntity != null && cameraEntity.isPtz() ? cameraEntity.getTiltDegrees() : 0.0F;
        Vec3 cameraPosition = new Vec3(
                pos.getX() + 0.5 + facing.getStepX() * 0.47,
                pos.getY() + 7.0 / 16.0,
                pos.getZ() + 0.5 + facing.getStepZ() * 0.47);
        CAMERA.aim(mc.level, mc.player, cameraPosition, facing.toYRot() + pan, 8.0F - tilt);

        feedClearAndBind(feed);
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
        mc.levelRenderer.renderLevel(pose, partialTick, Util.getNanos() + 2_000_000L, false, CAMERA,
                mc.gameRenderer, mc.gameRenderer.lightTexture(), projection);
    }

    private static void feedClearAndBind(Feed feed) {
        feed.target.setClearColor(0.035F, 0.04F, 0.05F, 1.0F);
        feed.target.clear(Minecraft.ON_OSX);
        feed.target.bindWrite(true);
    }
}

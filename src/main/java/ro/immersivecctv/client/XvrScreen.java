package ro.immersivecctv.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import ro.immersivecctv.CctvSounds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ro.immersivecctv.block.CameraBlock;
import ro.immersivecctv.block.XvrBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import ro.immersivecctv.CctvNetwork;

/** Interactive XVR console with a native Minecraft world-rendered live camera window. */
public class XvrScreen extends Screen {
    // Local DVR snapshots use this output size; the live view itself is rendered at native game resolution.
    private static final int FEED_W = 384;
    private static final int FEED_H = 216;
    private static final long COLD_BOOT_MS = 6200L;
    private static final long PER_CAMERA_CONNECT_MS = 1150L;
    private static final Map<BlockPos, Long> BOOT_COMPLETE_AT = new ConcurrentHashMap<>();
    private static final double H_FOV_TAN = Math.tan(Math.toRadians(39.0));
    private static final double V_FOV_TAN = Math.tan(Math.toRadians(27.0));
    private static final ResourceLocation FEED_TEXTURE_LOCATION = new ResourceLocation("immersivecctv", "dynamic/cctv_feed");

    private final BlockPos monitorPos;
    private final BlockPos remoteXvrPos;
    private int channel;
    private int filter;
    private boolean alarm = true;
    private boolean settings;
    private long openedAt;
    private int[] feedPixels = new int[0];
    private DynamicTexture feedTexture;
    private boolean widgetsForSignal;
    private boolean lastSignal;
    private boolean feedReady;
    private boolean playback;
    private boolean playbackPaused;
    private List<java.nio.file.Path> playbackFiles = List.of();
    private int playbackIndex = -1;
    private String playbackLabel = "";
    private long playbackNextAdvanceAt;
    private long noticeUntil;
    private int lastBootSoundPhase = -1;
    private String formMode = "";
    private String formMessage = "";
    private boolean formSubmitting;
    private long formSubmittedAt;
    private EditBox usernameField;
    private EditBox passwordField;
    private EditBox confirmPasswordField;
    private long nextScreenshotCaptureAt;
    private boolean nativeFeedLooksValid;
    private BlockPos lastNativeCaptureCameraPos;
    private final CameraFallbackRenderer fallbackRenderer = new CameraFallbackRenderer();

    public XvrScreen(BlockPos monitorPos) {
        super(Component.literal("IMMERSIVE XVR SURVEILLANCE // IUSTIN MARTAC"));
        this.monitorPos = monitorPos.immutable();
        this.remoteXvrPos = null;
    }

    /** Opens the same console UI over an authenticated in-world cloud session. */
    public XvrScreen(BlockPos xvrPos, boolean remote) {
        super(Component.literal(remote ? "IMMERSIVE CCTV // CLOUD REMOTE" : "IMMERSIVE XVR SURVEILLANCE // IUSTIN MARTAC"));
        this.monitorPos = remote ? null : xvrPos.immutable();
        this.remoteXvrPos = remote ? xvrPos.immutable() : null;
    }

    private boolean isRemote() { return remoteXvrPos != null; }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        widgetsForSignal = signal();
        lastSignal = widgetsForSignal;
        XvrBlockEntity currentXvr = xvr();
        if (currentXvr != null) {
            if (!widgetsForSignal) BOOT_COMPLETE_AT.remove(currentXvr.getBlockPos());
            else BOOT_COMPLETE_AT.computeIfAbsent(currentXvr.getBlockPos(), p -> System.currentTimeMillis() + COLD_BOOT_MS);
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        usernameField = null;
        passwordField = null;
        confirmPasswordField = null;
        formMode = desiredFormMode();
        formMessage = "";
        formSubmitting = false;
        if (!signal()) return;
        XvrBlockEntity active = xvr();
        if (active != null && System.currentTimeMillis() < BOOT_COMPLETE_AT.getOrDefault(active.getBlockPos(), 0L)) return;
        if (!formMode.isEmpty()) {
            buildCredentialForm();
            return;
        }
        List<BlockPos> cameras = connectedCameras();
        int count = cameras.size();
        int row1Y = 22;
        if (count > 0) {
            int gap = 2;
            int buttonW = Math.max(26, Math.min(48, (width - 8 - gap * (count - 1)) / count));
            int total = count * buttonW + (count - 1) * gap;
            int x = (width - total) / 2;
            for (int i = 0; i < count; i++) {
                final int selected = i;
                Button b = Button.builder(Component.literal(String.format(java.util.Locale.ROOT, "CAM %02d", i + 1)), button -> {
                    channel = selected;
                    playback = false;
                    playbackIndex = -1;
                    playbackFiles = List.of();
                    rebuildButtons();
                }).bounds(x + i * (buttonW + gap), row1Y, buttonW, 16).build();
                b.active = selected != channel;
                addRenderableWidget(b);
            }
        }
        int y = 40;
        if (settings) {
            int[] widths = {48, 45, 40, 52, 46, 38};
            String[] labels = {"FILTER", "ALARM", "CAM", "RST CAM", "OFF XVR", "BACK"};
            int total = 0; for (int w : widths) total += w;
            total += (widths.length - 1) * 2;
            int x = Math.max(2, (width - total) / 2);
            addTopButton(labels[0], x, y, widths[0], () -> { filter = (filter + 1) % 3; playUiSound("button_click", 0.95F); rebuildButtons(); }, true); x += widths[0] + 2;
            addTopButton(labels[1], x, y, widths[1], () -> { alarm = !alarm; playUiSound(alarm ? "alarm_on" : "alarm_off", 1.0F); rebuildButtons(); }, true); x += widths[1] + 2;
            addTopButton(labels[2], x, y, widths[2], () -> { XvrBlockEntity bx = xvr(); BlockPos cp = currentCameraPos(); if (bx != null && cp != null) CctvNetwork.sendCommand(bx.getBlockPos(), cp, 0); }, currentCameraPos() != null); x += widths[2] + 2;
            addTopButton(labels[3], x, y, widths[3], () -> { XvrBlockEntity bx = xvr(); BlockPos cp = currentCameraPos(); if (bx != null && cp != null) CctvNetwork.sendCommand(bx.getBlockPos(), cp, 1); }, currentCameraPos() != null); x += widths[3] + 2;
            addTopButton(labels[4], x, y, widths[4], () -> { XvrBlockEntity bx = xvr(); if (bx != null) CctvNetwork.sendCommand(bx.getBlockPos(), null, 2); }, xvr() != null); x += widths[4] + 2;
            addTopButton(labels[5], x, y, widths[5], () -> { settings = false; rebuildButtons(); }, true);
            return;
        }
        BlockPos camPos = currentCameraPos();
        CameraBlockEntity camera = camPos != null && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(camPos) instanceof CameraBlockEntity found ? found : null;
        boolean ptz = camera != null && camera.isPtz();
        int[] widths = {22, 22, 22, 22, 30, 42, 34, 22, 22, 40};
        String[] labels = {"◀", "▶", "▲", "▼", "HOME", playback ? (playbackPaused ? "RESUME" : "PAUSE") : "PLAY", "LIVE", "◂", "▸", "SET"};
        int total = 0; for (int w : widths) total += w;
        total += (widths.length - 1) * 2;
        int x = Math.max(2, (width - total) / 2);
        int[] actions = {3, 4, 5, 6};
        for (int i = 0; i < 4; i++) {
            final int action = actions[i];
            addTopButton(labels[i], x, y, widths[i], () -> sendPtz(action), ptz);
            x += widths[i] + 2;
        }
        addTopButton(labels[4], x, y, widths[4], () -> sendPtz(7), ptz); x += widths[4] + 2;
        addTopButton(labels[5], x, y, widths[5], () -> { if (playback) togglePlaybackPaused(); else togglePlayback(); }, true); x += widths[5] + 2;
        addTopButton(labels[6], x, y, widths[6], () -> togglePlayback(), playback); x += widths[6] + 2;
        addTopButton(labels[7], x, y, widths[7], () -> movePlayback(-1), playback && playbackIndex > 0); x += widths[7] + 2;
        addTopButton(labels[8], x, y, widths[8], () -> movePlayback(1), playback && playbackIndex >= 0 && playbackIndex < playbackFiles.size() - 1); x += widths[8] + 2;
        addTopButton(labels[9], x, y, widths[9], () -> { settings = true; rebuildButtons(); }, true);
    }

    private String desiredFormMode() {
        if (!signal()) return "";
        XvrBlockEntity activeXvr = xvr();
        if (activeXvr == null) return "";
        if (System.currentTimeMillis() < BOOT_COMPLETE_AT.getOrDefault(activeXvr.getBlockPos(), 0L)) return "";
        if (!isRemote() && !activeXvr.isAccountConfigured()) return "xvr-setup";
        if (playback) return "";
        BlockPos selected = currentCameraPos();
        CameraBlockEntity camera = selected == null ? null : cameraEntity(selected);
        if (camera == null || camera.isRestarting()) return "";
        if (!camera.isAuthenticated()) return "camera-login";
        if (camera.mustChangePassword()) return "camera-change";
        if (!camera.isOnline()) return "";
        return "";
    }

    private void buildCredentialForm() {
        int fieldWidth = Math.min(250, Math.max(150, width - 32));
        int left = (width - fieldWidth) / 2;
        int cy = height / 2;
        if (formMode.equals("xvr-setup")) {
            usernameField = new EditBox(font, left, cy - 45, fieldWidth, 20, Component.literal("Username"));
            usernameField.setMaxLength(32);
            usernameField.setHint(Component.literal("Create administrator username"));
            passwordField = new EditBox(font, left, cy - 18, fieldWidth, 20, Component.literal("Password"));
            passwordField.setMaxLength(64);
            passwordField.setHint(Component.literal("Create password (4-64 characters)"));
            confirmPasswordField = new EditBox(font, left, cy + 9, fieldWidth, 20, Component.literal("Confirm password"));
            confirmPasswordField.setMaxLength(64);
            confirmPasswordField.setHint(Component.literal("Confirm password"));
            addRenderableWidget(usernameField);
            addRenderableWidget(passwordField);
            addRenderableWidget(confirmPasswordField);
            addRenderableWidget(Button.builder(Component.literal(formSubmitting ? "SAVING..." : "CREATE XVR ACCOUNT"), b -> submitCredentialForm())
                    .bounds(width / 2 - 100, cy + 38, 200, 20).build());
            setInitialFocus(usernameField);
        } else if (formMode.equals("camera-login")) {
            usernameField = new EditBox(font, left, cy - 24, fieldWidth, 20, Component.literal("Camera username"));
            usernameField.setMaxLength(32);
            usernameField.setValue("admin");
            passwordField = new EditBox(font, left, cy + 3, fieldWidth, 20, Component.literal("Camera password"));
            passwordField.setMaxLength(64);
            passwordField.setHint(Component.literal("Camera password"));
            addRenderableWidget(usernameField);
            addRenderableWidget(passwordField);
            addRenderableWidget(Button.builder(Component.literal("AUTHENTICATE CAMERA"), b -> submitCredentialForm())
                    .bounds(width / 2 - 100, cy + 32, 200, 20).build());
            setInitialFocus(passwordField);
        } else if (formMode.equals("camera-change")) {
            passwordField = new EditBox(font, left, cy - 24, fieldWidth, 20, Component.literal("New camera password"));
            passwordField.setMaxLength(64);
            passwordField.setHint(Component.literal("Choose a new password"));
            confirmPasswordField = new EditBox(font, left, cy + 3, fieldWidth, 20, Component.literal("Confirm new password"));
            confirmPasswordField.setMaxLength(64);
            confirmPasswordField.setHint(Component.literal("Repeat new password"));
            addRenderableWidget(passwordField);
            addRenderableWidget(confirmPasswordField);
            addRenderableWidget(Button.builder(Component.literal("CHANGE CAMERA PASSWORD"), b -> submitCredentialForm())
                    .bounds(width / 2 - 100, cy + 32, 200, 20).build());
            setInitialFocus(passwordField);
        }
    }

    private void submitCredentialForm() {
        XvrBlockEntity activeXvr = xvr();
        BlockPos selected = currentCameraPos();
        CameraBlockEntity camera = selected == null ? null : cameraEntity(selected);
        String username = usernameField == null ? "" : usernameField.getValue().trim();
        String password = passwordField == null ? "" : passwordField.getValue();
        String confirmation = confirmPasswordField == null ? "" : confirmPasswordField.getValue();
        if (activeXvr == null) return;
        if (formMode.equals("xvr-setup")) {
            if (username.length() < 3 || username.length() > 32) {
                formMessage = "Username must contain 3-32 characters.";
                return;
            }
            if (password.length() < 4 || password.length() > 64) {
                formMessage = "Password must contain 4-64 characters.";
                return;
            }
            if (!password.equals(confirmation)) {
                formMessage = "The passwords do not match.";
                return;
            }
            CctvNetwork.sendXvrSetup(activeXvr.getBlockPos(), username, password);
            formSubmitting = true;
            formSubmittedAt = System.currentTimeMillis();
            formMessage = "Saving administrator account...";
        } else if (formMode.equals("camera-login")) {
            if (camera == null) return;
            if (username.isBlank() || password.isEmpty()) {
                formMessage = "Enter the camera username and password.";
                return;
            }
            CctvNetwork.sendCameraLogin(activeXvr.getBlockPos(), selected, username, password);
            formSubmittedAt = System.currentTimeMillis();
            formMessage = "Checking credentials...";
        } else if (formMode.equals("camera-change")) {
            if (camera == null) return;
            if (password.length() < 4 || password.length() > 64) {
                formMessage = "New password must contain 4-64 characters.";
                return;
            }
            if (!password.equals(confirmation)) {
                formMessage = "The passwords do not match.";
                return;
            }
            CctvNetwork.sendCameraPasswordChange(activeXvr.getBlockPos(), selected, password);
            formSubmittedAt = System.currentTimeMillis();
            formMessage = "Saving new camera password...";
        }
    }

    private void addTopButton(String label, int x, int y, int w, Runnable action, boolean active) {
        Button button = Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 16).build();
        button.active = active;
        addRenderableWidget(button);
    }

    private void sendPtz(int action) {
        XvrBlockEntity bx = xvr();
        BlockPos cp = currentCameraPos();
        if (bx != null && cp != null) CctvNetwork.sendCommand(bx.getBlockPos(), cp, action);
    }

    private void togglePlayback() {
        if (playback) {
            playback = false;
            playbackPaused = false;
            playbackIndex = -1;
            playbackFiles = List.of();
            playbackLabel = "";
            feedReady = false;
            rebuildButtons();
            return;
        }
        BlockPos camera = currentCameraPos();
        playbackFiles = LocalRecorder.recordings(Minecraft.getInstance(), camera);
        if (playbackFiles.isEmpty()) {
            playbackLabel = "NO SAVED RECORDINGS YET";
            noticeUntil = System.currentTimeMillis() + 3000L;
            rebuildButtons();
            return;
        }
        playback = true;
        playbackPaused = false;
        playbackIndex = 0;
        playbackNextAdvanceAt = System.currentTimeMillis() + 850L;
        loadPlaybackFrame();
        rebuildButtons();
    }

    private void togglePlaybackPaused() {
        if (!playback) return;
        playbackPaused = !playbackPaused;
        playbackNextAdvanceAt = System.currentTimeMillis() + 850L;
        rebuildButtons();
    }

    private void movePlayback(int delta) {
        if (!playback || playbackFiles.isEmpty()) return;
        playbackPaused = true;
        int next = playbackIndex + delta;
        if (next < 0 || next >= playbackFiles.size()) return;
        playbackIndex = next;
        playbackNextAdvanceAt = System.currentTimeMillis() + 1300L;
        loadPlaybackFrame();
        rebuildButtons();
    }

    private void loadPlaybackFrame() {
        if (playbackIndex < 0 || playbackIndex >= playbackFiles.size()) return;
        try {
            java.nio.file.Path file = playbackFiles.get(playbackIndex);
            int[] saved = LocalRecorder.load(file, FEED_W, FEED_H);
            feedPixels = new int[saved.length];
            for (int i = 0; i < saved.length; i++) feedPixels[i] = saved[i] & 0xFFFFFF;
            feedReady = true;
            playbackLabel = LocalRecorder.label(file);
            uploadFeedTexture(Minecraft.getInstance());
        } catch (java.io.IOException exception) {
            playbackLabel = "RECORDING FILE ERROR";
            feedReady = false;
        }
    }

    private String filterName() {
        return switch (filter) { case 1 -> "NIGHT IR"; case 2 -> "THERMAL"; default -> "STANDARD HD"; };
    }

    private MonitorBlockEntity monitor() {
        Level level = Minecraft.getInstance().level;
        return !isRemote() && level != null && level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor ? monitor : null;
    }

    private XvrBlockEntity xvr() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (isRemote()) return level.getBlockEntity(remoteXvrPos) instanceof XvrBlockEntity recorder ? recorder : null;
        MonitorBlockEntity monitor = monitor();
        if (monitor == null || monitor.getXvrPos() == null) return null;
        BlockEntity be = level.getBlockEntity(monitor.getXvrPos());
        return be instanceof XvrBlockEntity recorder ? recorder : null;
    }

    private boolean hdmiConnected() {
        XvrBlockEntity xvr = xvr();
        if (isRemote()) return xvr != null && xvr.canObtainCloudEndpoint();
        MonitorBlockEntity monitor = monitor();
        return monitor != null && xvr != null && monitorPos.equals(xvr.getMonitorPos())
                && xvr.getBlockPos().equals(monitor.getXvrPos());
    }

    private boolean signal() {
        XvrBlockEntity xvr = xvr();
        Level level = Minecraft.getInstance().level;
        if (xvr == null || level == null || !hdmiConnected() || !xvr.hasWorkingPower()
                || !level.getBlockState(xvr.getBlockPos()).hasProperty(XvrBlock.POWERED)
                || !level.getBlockState(xvr.getBlockPos()).getValue(XvrBlock.POWERED)) return false;
        if (isRemote()) return xvr.isAccountConfigured() && xvr.canObtainCloudEndpoint();
        MonitorBlockEntity monitor = monitor();
        return monitor != null && monitor.hasWorkingPower();
    }

    private List<BlockPos> connectedCameras() {
        List<BlockPos> valid = new ArrayList<>();
        XvrBlockEntity xvr = xvr();
        Level level = Minecraft.getInstance().level;
        if (xvr == null || level == null) return valid;
        for (BlockPos pos : xvr.getCameras()) {
            if (level.getBlockEntity(pos) instanceof CameraBlockEntity camera && xvr.getBlockPos().equals(camera.getXvrPos())) {
                valid.add(pos);
            }
        }
        return valid;
    }

    private void playUiSound(String id, float pitch) {
        Level level = Minecraft.getInstance().level;
        BlockPos soundPos = isRemote() ? remoteXvrPos : monitorPos;
        if (level != null && soundPos != null) level.playLocalSound(soundPos, CctvSounds.get(id), SoundSource.BLOCKS, 0.35F, pitch, false);
    }

    private BlockPos currentCameraPos() {
        List<BlockPos> cameras = connectedCameras();
        if (cameras.isEmpty()) return null;
        channel = Math.floorMod(channel, cameras.size());
        return cameras.get(channel);
    }

    private Direction cameraFacing(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(pos).hasProperty(CameraBlock.FACING)) {
            return level.getBlockState(pos).getValue(CameraBlock.FACING);
        }
        return Direction.NORTH;
    }

    private Vec3 cameraOrigin(BlockPos pos) {
        Direction facing = cameraFacing(pos);
        Vec3 forward = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        // Start just outside the camera block so a ray never collides with its own housing.
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.61, pos.getZ() + 0.5).add(forward.scale(0.52));
    }

    private Vec3 forward(BlockPos pos) {
        Direction facing = cameraFacing(pos);
        Vec3 base = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        CameraBlockEntity camera = cameraEntity(pos);
        double pan = camera != null && camera.isPtz() ? Math.toRadians(camera.getPanDegrees()) : 0.0;
        double tilt = camera != null && camera.isPtz() ? Math.toRadians(camera.getTiltDegrees()) : 0.0;
        double hx = base.x * Math.cos(pan) + base.z * Math.sin(pan);
        double hz = -base.x * Math.sin(pan) + base.z * Math.cos(pan);
        return new Vec3(hx * Math.cos(tilt), Math.sin(tilt), hz * Math.cos(tilt)).normalize();
    }

    private CameraBlockEntity cameraEntity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof CameraBlockEntity camera ? camera : null;
    }

    private Vec3 right(BlockPos pos) {
        Vec3 f = forward(pos);
        return new Vec3(-f.z, 0, f.x).normalize();
    }

    private Vec3 cameraUp(BlockPos pos) {
        Vec3 f = forward(pos);
        Vec3 r = right(pos);
        return new Vec3(r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x).normalize();
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean live = signal();
        if (live != lastSignal) {
            XvrBlockEntity bx = xvr();
            if (bx != null) {
                if (!live) BOOT_COMPLETE_AT.remove(bx.getBlockPos());
                else BOOT_COMPLETE_AT.put(bx.getBlockPos(), System.currentTimeMillis() + COLD_BOOT_MS);
            }
            if (live) lastBootSoundPhase = -1;
            lastSignal = live;
            widgetsForSignal = live;
            rebuildButtons();
        }
        XvrBlockEntity activeXvr = xvr();
        long now = System.currentTimeMillis();
        if (playback && !playbackPaused && playbackIndex >= 0 && now >= playbackNextAdvanceAt) {
            if (playbackIndex < playbackFiles.size() - 1) {
                playbackIndex++;
                loadPlaybackFrame();
            }
            if (playbackIndex >= playbackFiles.size() - 1) playbackPaused = true;
            playbackNextAdvanceAt = now + 850L;
            rebuildButtons();
        }
        long bootCompleteAt = activeXvr == null ? 0L : BOOT_COMPLETE_AT.getOrDefault(activeXvr.getBlockPos(), 0L);
        boolean booting = live && now < bootCompleteAt;
        BlockPos cameraPos = live && !booting ? currentCameraPos() : null;
        String wantedFormMode = desiredFormMode();
        if (!wantedFormMode.equals(formMode)) rebuildButtons();
        if (!formMessage.isEmpty() && formSubmittedAt > 0L && now - formSubmittedAt > 2200L) {
            if (formMode.equals("xvr-setup") && formSubmitting) {
                formSubmitting = false;
                formMessage = "Setup did not complete. Check the console state and retry.";
            } else if (formMode.equals("camera-login") && formMessage.equals("Checking credentials...")) {
                formMessage = "Login failed or no response. Check the credentials and retry.";
            } else if (formMode.equals("camera-change") && formMessage.equals("Saving new camera password...")) {
                formMessage = "Password change failed or no response. Try again.";
            }
            formSubmittedAt = 0L;
        }
        CameraBlockEntity activeCamera = cameraPos == null ? null : cameraEntity(cameraPos);
        boolean nativeCameraFeed = live && !booting && activeXvr != null && activeXvr.isAccountConfigured()
                && !playback && cameraPos != null && activeCamera != null && activeCamera.isOnline()
                && activeCamera.isAuthenticated() && !activeCamera.mustChangePassword() && Minecraft.getInstance().level != null;
        if (nativeCameraFeed) {
            CameraViewController.show(cameraPos, cameraFacing(cameraPos), activeCamera.getPanDegrees(), activeCamera.getTiltDegrees());
        } else {
            CameraViewController.restore();
        }
        int margin = 2;
        int top = 60;
        int bottom = height - 2;
        int viewX = margin, viewY = top, viewW = width - 2 * margin, viewH = bottom - top;
        if (nativeCameraFeed) {
            if (lastNativeCaptureCameraPos == null || !lastNativeCaptureCameraPos.equals(cameraPos)) {
                lastNativeCaptureCameraPos = cameraPos.immutable();
                nativeFeedLooksValid = false;
                nextScreenshotCaptureAt = 0L;
            }
            // Keep a real 16:9 window inside the XVR UI. Minecraft renders the full world at native
            // framebuffer resolution; this central crop is just the physical monitor's viewport.
            int availableW = Math.max(1, width - 20);
            int availableH = Math.max(1, height - top - 12);
            viewW = Math.min(availableW, (int) Math.floor(availableH * 16.0 / 9.0));
            viewH = Math.min(availableH, (int) Math.floor(viewW * 9.0 / 16.0));
            viewX = (width - viewW) / 2;
            viewY = top + Math.max(0, (availableH - viewH) / 2);
            bottom = viewY + viewH;
            // Opaque masks hide the player's normal world around the active camera window.
            g.fill(0, 20, width, Math.max(20, viewY - 2), 0xFF05080C);
            g.fill(0, viewY - 2, Math.max(0, viewX - 2), bottom + 2, 0xFF05080C);
            g.fill(Math.min(width, viewX + viewW + 2), viewY - 2, width, bottom + 2, 0xFF05080C);
            g.fill(0, bottom + 2, width, height, 0xFF05080C);
            // IMPORTANT: only paint the four bezel strips. Filling the entire rectangle here
            // covered the native 3D camera view with an opaque colour and made the feed appear
            // black/flat even though Minecraft had already rendered the camera's world view.
            g.fill(viewX - 2, viewY - 2, viewX + viewW + 2, viewY, 0xFF26343A);
            g.fill(viewX - 2, bottom, viewX + viewW + 2, bottom + 2, 0xFF26343A);
            g.fill(viewX - 2, viewY, viewX, bottom, 0xFF26343A);
            g.fill(viewX + viewW, viewY, viewX + viewW + 2, bottom, 0xFF26343A);
        } else {
            g.fill(0, 0, width, height, 0xFF05080C);
            g.fill(margin - 2, top - 2, width - margin + 2, bottom + 2, 0xFF26343A);
            g.fill(margin, top, width - margin, bottom, 0xFF020304);
        }

        if (!live) {
            int boxW = Math.min(360, width - margin * 2 - 20);
            int boxH = 36;
            int t = (int) ((System.currentTimeMillis() - openedAt) / 22L);
            int maxX = Math.max(0, width - margin * 2 - boxW);
            int maxY = Math.max(0, bottom - top - boxH);
            int x = margin + (int) (Math.abs(Math.sin(t * 0.043)) * maxX);
            int y = top + (int) (Math.abs(Math.cos(t * 0.031)) * maxY);
            g.fill(x, y, x + boxW, y + boxH, 0xFF500D11);
            g.fill(x + 1, y + 1, x + boxW - 1, y + boxH - 1, 0xFF050505);
            g.drawCenteredString(font, "NO SIGNAL - CHECK HDMI / XVR POWER", x + boxW / 2, y + 7, 0xFFFF4040);
            g.drawCenteredString(font, "Physical connection required", x + boxW / 2, y + 20, 0xFFB9B9B9);
        } else if (booting) {
            long elapsed = COLD_BOOT_MS - (bootCompleteAt - now);
            int bootPhase = elapsed < 550 ? 0 : elapsed < 1450 ? 1 : elapsed < 2500 ? 2 : elapsed < 3800 ? 3 : 4;
            if (bootPhase > lastBootSoundPhase && activeXvr != null && Minecraft.getInstance().level != null) {
                Minecraft.getInstance().level.playLocalSound(activeXvr.getBlockPos(), CctvSounds.randomVariant("startup_beep_", 48),
                        SoundSource.BLOCKS, 0.36F, 0.85F + bootPhase * 0.10F, false);
                lastBootSoundPhase = bootPhase;
            }
            int cx = width / 2;
            g.drawCenteredString(font, "=== IMMERSIVE XVR SURVEILLANCE ===", cx, height / 2 - 48, 0xFF71F4E2);
            g.drawCenteredString(font, "Firmware v1.4.0 // SECURE BOOT", cx, height / 2 - 25, 0xFFCADDE0);
            if (elapsed > 550) g.drawCenteredString(font, "[ OK ] XVR kernel / memory initialised", cx, height / 2 - 7, 0xFF67F4A1);
            if (elapsed > 1450) g.drawCenteredString(font, "[ OK ] AC 12V input and PoE controller scan", cx, height / 2 + 7, 0xFF67F4A1);
            if (elapsed > 2500) g.drawCenteredString(font, "[ OK ] HDD SMART / filesystem check", cx, height / 2 + 21, 0xFF67F4A1);
            if (elapsed > 3800) g.drawCenteredString(font, "[ OK ] Video decoder and HDMI output", cx, height / 2 + 35, 0xFF67F4A1);
            int barW = Math.min(260, width - margin * 2);
            int barX = cx - barW / 2;
            g.fill(barX, height / 2 + 52, barX + barW, height / 2 + 58, 0xFF24323A);
            g.fill(barX, height / 2 + 52, barX + (int) (barW * Math.min(1.0, elapsed / (double) COLD_BOOT_MS)), height / 2 + 58, 0xFF42D9C7);
        } else if (!formMode.isEmpty()) {
            int boxW = Math.min(330, width - 24);
            int boxL = (width - boxW) / 2;
            g.fill(boxL, height / 2 - 83, boxL + boxW, height / 2 + 83, 0xF009151D);
            g.fill(boxL, height / 2 - 83, boxL + boxW, height / 2 - 80, 0xFF43D9C7);
            if (formMode.equals("xvr-setup")) {
                g.drawCenteredString(font, "FIRST BOOT // CREATE XVR ADMIN", width / 2, height / 2 - 72, 0xFF71F4E2);
                g.drawCenteredString(font, "This account is saved until this XVR is broken.", width / 2, height / 2 - 58, 0xFFB8CBD0);
                g.drawCenteredString(font, "Choose a username and password to continue.", width / 2, height / 2 - 8, 0xFFB8CBD0);
            } else if (formMode.equals("camera-login")) {
                g.drawCenteredString(font, "CAMERA AUTHENTICATION REQUIRED", width / 2, height / 2 - 67, 0xFF71F4E2);
                String id = activeCamera == null ? "UNKNOWN" : activeCamera.getCameraId();
                g.drawCenteredString(font, "ID: " + id, width / 2, height / 2 - 49, 0xFFB8CBD0);
                if (activeCamera != null && activeCamera.hasChangedPassword()) {
                    g.drawCenteredString(font, "Use the previously configured camera credentials.", width / 2, height / 2 + 29, 0xFFFFD778);
                } else {
                    g.drawCenteredString(font, "Default credentials: admin / 0000", width / 2, height / 2 + 29, 0xFFFFD778);
                    g.drawCenteredString(font, "A first successful login requires a password change.", width / 2, height / 2 + 46, 0xFFB8CBD0);
                }
            } else {
                g.drawCenteredString(font, "SECURITY ACTION REQUIRED", width / 2, height / 2 - 65, 0xFFFFD778);
                g.drawCenteredString(font, "Change the default camera password to enable video.", width / 2, height / 2 + 29, 0xFFB8CBD0);
                g.drawCenteredString(font, "Use at least 4 characters.", width / 2, height / 2 + 46, 0xFFB8CBD0);
            }
            if (!formMessage.isEmpty()) g.drawCenteredString(font, formMessage, width / 2, height / 2 + 68, formMessage.startsWith("Saving") || formMessage.startsWith("Checking") ? 0xFF77F4AA : 0xFFFF7D7D);
        } else if (settings) {
            g.fill(width / 2 - 140, top + 48, width / 2 + 140, top + 174, 0xD9071119);
            g.drawCenteredString(font, "XVR SETTINGS", width / 2, top + 60, 0xFF79E5F2);
            g.drawCenteredString(font, "VIDEO FILTER: " + filterName(), width / 2, top + 84, 0xFFB7D8E0);
            g.drawCenteredString(font, "MOTION ALARM: " + (alarm ? "ENABLED" : "DISABLED"), width / 2, top + 101, alarm ? 0xFF78F39B : 0xFFB0B0B0);
            g.drawCenteredString(font, "LOCAL DVR RECORDINGS: " + LocalRecorder.recordings(Minecraft.getInstance(), currentCameraPos()).size() + " FRAMES", width / 2, top + 118, 0xFF78F39B);
            g.drawCenteredString(font, "PTZ control is available on PTZ cameras.", width / 2, top + 135, 0xFF9AAEB5);
            g.drawCenteredString(font, "CAM ON/OFF  //  RESTART  //  SHUTDOWN XVR", width / 2, top + 151, 0xFF9AAEB5);
        } else if (cameraPos == null) {
            drawNoCameraPattern(g, margin, top, width - margin, bottom);
            g.drawCenteredString(font, "NO CAMERA CONNECTED", width / 2, (top + bottom) / 2 - 8, 0xFFFF5555);
            g.drawCenteredString(font, "Connect a PoE cable to a rear camera jack and an XVR RJ45 port.", width / 2, (top + bottom) / 2 + 9, 0xFFB0CED3);
        } else if (!playback && cameraPos != null && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(cameraPos) instanceof CameraBlockEntity cameraDevice
                && !cameraDevice.isOnline()) {
            drawNoCameraPattern(g, margin, top, width - margin, bottom);
            String offlineText = cameraDevice.isRestarting() ? "CAMERA RESTARTING..." : "CAMERA DISABLED IN XVR SETTINGS";
            g.drawCenteredString(font, offlineText, width / 2, (top + bottom) / 2 - 8, 0xFFFF5A5A);
            g.drawCenteredString(font, "PoE link detected // image transmission paused", width / 2, (top + bottom) / 2 + 10, 0xFFB0CED3);
        } else if (!playback && activeXvr != null && now < bootCompleteAt + PER_CAMERA_CONNECT_MS * (channel + 1L)) {
            drawNoCameraPattern(g, margin, top, width - margin, bottom);
            g.drawCenteredString(font, String.format(java.util.Locale.ROOT, "CAM %02d // CONNECTING...", channel + 1), width / 2, (top + bottom) / 2 - 8, 0xFF78E5F2);
            int barW = Math.min(220, width - margin * 2 - 20);
            int barX = width / 2 - barW / 2;
            g.fill(barX, (top + bottom) / 2 + 10, barX + barW, (top + bottom) / 2 + 16, 0xFF24323A);
            long startAt = bootCompleteAt + PER_CAMERA_CONNECT_MS * channel;
            float progress = (float) Math.max(0.0, Math.min(1.0, (now - startAt) / (double) PER_CAMERA_CONNECT_MS));
            g.fill(barX, (top + bottom) / 2 + 10, barX + (int) (barW * progress), (top + bottom) / 2 + 16, 0xFF42D9C7);
        } else {
            if (playback) {
                drawCameraPixels(g, viewX, viewY, viewW, viewH);
            } else if (nativeCameraFeed) {
                captureNativeFeedFrame(cameraPos, viewX, viewY, viewW, viewH, now);
                // Keep the real world-rendered view when it is valid; if a renderer, shader, or camera
                // lifecycle issue produces a black frame, fall back to the textured preview so the
                // CCTV monitor never silently becomes a completely black rectangle.
                if (!nativeFeedLooksValid) fallbackRenderer.render(g, cameraPos, filter, viewX, viewY, viewW, viewH);
            } else {
                drawNoCameraPattern(g, viewX, viewY, viewX + viewW, viewY + viewH);
            }
            for (int y = viewY + 2; y < bottom; y += 8) g.fill(viewX, y, viewX + viewW, y + 1, 0x08000000);
            String camName = String.format(java.util.Locale.ROOT, "CAM %02d", channel + 1);
            g.fill(viewX + 3, viewY + 3, viewX + Math.min(viewW - 3, 190), viewY + 18, 0x99040A10);
            g.drawString(font, playback ? "PLAYBACK // " + camName : "LIVE CAMERA // " + camName, viewX + 8, viewY + 7, filter == 1 ? 0xFF49FF65 : 0xFF79E5F2, false);
            drawCrosshair(g, viewX + viewW / 2, viewY + viewH / 2, 0x987DE9F5);
            if (!playback) drawTargets(g, targets(), cameraPos, viewX, viewY, viewW, viewH);
            if (!playback && now < noticeUntil && !playbackLabel.isEmpty()) {
                g.fill(viewX + viewW / 2 - 95, viewY + viewH / 2 - 12, viewX + viewW / 2 + 95, viewY + viewH / 2 + 12, 0xD9061119);
                g.drawCenteredString(font, playbackLabel, viewX + viewW / 2, viewY + viewH / 2 - 4, 0xFFFFD778);
            }
            if (playback) {
                String stamp = playbackLabel.isEmpty() ? "PLAYBACK" : playbackLabel;
                g.fill(viewX + 3, viewY + viewH - 18, viewX + Math.min(viewW - 3, font.width(stamp) + 18), viewY + viewH - 3, 0xA0040A10);
                g.drawString(font, stamp, viewX + 8, viewY + viewH - 13, 0xFFFFD778, false);
            } else {
                g.fill(viewX + 3, viewY + viewH - 18, viewX + Math.min(viewW - 3, 250), viewY + viewH - 3, 0xA0040A10);
                g.drawString(font, "XVR LIVE  •  " + cameraPos.getX() + " " + cameraPos.getY() + " " + cameraPos.getZ(), viewX + 8, viewY + viewH - 13, 0xFFB0CED3, false);
                g.drawString(font, "REC", Math.max(viewX + 8, viewX + viewW - 31), viewY + 7, 0xFFFF3333, false);
            }
        }

        g.fill(0, 0, width, 20, 0xFF111B23);
        g.drawString(font, isRemote() ? "IMMERSIVE CCTV // CLOUD REMOTE" : "IMMERSIVE CCTV // IUSTIN MARTAC", 6, 6, 0xFFB8E8EE, false);
        String poe = connectedCameras().isEmpty() ? "POE OFFLINE" : "POE ONLINE";
        g.drawString(font, poe, Math.max(6, width - font.width(poe) - 8), 6, connectedCameras().isEmpty() ? 0xFFB0A0A0 : 0xFF6AFF9A, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Copies only the rendered monitor viewport for local DVR playback, avoiding expensive full-screen readbacks. */
    private void captureNativeFeedFrame(BlockPos cameraPos, int guiX, int guiY, int guiW, int guiH, long now) {
        if (now < nextScreenshotCaptureAt) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || cameraPos == null || width <= 0 || height <= 0) {
            nativeFeedLooksValid = false;
            return;
        }
        RenderTarget target = minecraft.getMainRenderTarget();
        int fbW = target.width;
        int fbH = target.height;
        if (fbW <= 0 || fbH <= 0) { nativeFeedLooksValid = false; return; }

        int left = clamp((int) Math.floor(guiX * fbW / (double) width), 0, fbW - 1);
        int top = clamp((int) Math.floor(guiY * fbH / (double) height), 0, fbH - 1);
        int sourceW = Math.max(1, Math.min(fbW - left, (int) Math.ceil(guiW * fbW / (double) width)));
        int sourceH = Math.max(1, Math.min(fbH - top, (int) Math.ceil(guiH * fbH / (double) height)));
        int glBottom = Math.max(0, fbH - top - sourceH);
        ByteBuffer pixels = BufferUtils.createByteBuffer(sourceW * sourceH * 4);
        boolean readBound = false;
        try {
            RenderSystem.assertOnRenderThread();
            target.bindRead();
            readBound = true;
            GL11.glReadPixels(left, glBottom, sourceW, sourceH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } catch (RuntimeException captureFailure) {
            nativeFeedLooksValid = false;
            nextScreenshotCaptureAt = now + 2500L;
            return;
        } finally {
            if (readBound) target.unbindRead();
        }

        // Sample a coarse grid from the captured viewport. A truly black framebuffer is not a valid
        // feed; very dark scenes are allowed as long as the image has some variation.
        long luminanceSum = 0L, luminanceSquaredSum = 0L;
        int sampled = 0, lit = 0;
        int sampleStepX = Math.max(1, sourceW / 80);
        int sampleStepY = Math.max(1, sourceH / 45);
        for (int sy = 0; sy < sourceH; sy += sampleStepY) {
            int gy = sourceH - 1 - sy;
            for (int sx = 0; sx < sourceW; sx += sampleStepX) {
                int offset = (gy * sourceW + sx) * 4;
                int rr = pixels.get(offset) & 255;
                int gg = pixels.get(offset + 1) & 255;
                int bb = pixels.get(offset + 2) & 255;
                int lum = (rr * 30 + gg * 59 + bb * 11) / 100;
                luminanceSum += lum;
                luminanceSquaredSum += (long) lum * lum;
                if (lum > 16) lit++;
                sampled++;
            }
        }
        double average = sampled == 0 ? 0.0 : luminanceSum / (double) sampled;
        double variance = sampled == 0 ? 0.0 : luminanceSquaredSum / (double) sampled - average * average;
        nativeFeedLooksValid = sampled > 0 && lit / (double) sampled > 0.08 && average > 7.0 && variance > 6.0;

        int[] rgb = new int[FEED_W * FEED_H];
        for (int py = 0; py < FEED_H; py++) {
            int sourceYFromTop = Math.min(sourceH - 1, (int) ((py + 0.5) * sourceH / FEED_H));
            int glY = sourceH - 1 - sourceYFromTop;
            for (int px = 0; px < FEED_W; px++) {
                int sourceX = Math.min(sourceW - 1, (int) ((px + 0.5) * sourceW / FEED_W));
                int offset = ((glY * sourceW) + sourceX) * 4;
                int r = pixels.get(offset) & 255;
                int gg = pixels.get(offset + 1) & 255;
                int b = pixels.get(offset + 2) & 255;
                rgb[py * FEED_W + px] = (r << 16) | (gg << 8) | b;
            }
        }
        nextScreenshotCaptureAt = now + 2500L;
        if (nativeFeedLooksValid) LocalRecorder.recordFrame(minecraft, cameraPos, rgb, FEED_W, FEED_H);
    }

    /** Uploads the selected locally recorded PNG frame as a dynamic texture for DVR playback. */
    private void uploadFeedTexture(Minecraft minecraft) {
        RenderSystem.assertOnRenderThread();
        NativeImage image = new NativeImage(FEED_W, FEED_H, false);
        try {
            for (int y = 0; y < FEED_H; y++) {
                for (int x = 0; x < FEED_W; x++) {
                    int rgb = feedPixels[y * FEED_W + x] & 0xFFFFFF;
                    int r = (rgb >>> 16) & 255;
                    int green = (rgb >>> 8) & 255;
                    int b = rgb & 255;
                    // NativeImage's packed RGBA integer layout is ABGR on the little-endian client.
                    image.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (green << 8) | r);
                }
            }
            if (feedTexture != null) {
                minecraft.getTextureManager().release(FEED_TEXTURE_LOCATION);
                feedTexture = null;
            }
            feedTexture = new DynamicTexture(image);
            minecraft.getTextureManager().register(FEED_TEXTURE_LOCATION, feedTexture);
            feedTexture.upload();
        } catch (RuntimeException exception) {
            image.close();
            throw exception;
        }
    }

    private void drawCameraPixels(GuiGraphics g, int x, int y, int w, int h) {
        if (!feedReady || feedTexture == null || feedPixels.length != FEED_W * FEED_H) {
            g.fill(x, y, x + w, y + h, 0xFF07121A);
            return;
        }
        // Use the source-size overload to prevent the feed from tiling on large displays.
        g.blit(FEED_TEXTURE_LOCATION, x, y, w, h, 0.0F, 0.0F, FEED_W, FEED_H, FEED_W, FEED_H);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // The console paints its own opaque masks. Vanilla's translucent screen background would
        // otherwise tint the live camera viewport before the UI is drawn over it.
    }

    @Override
    public void onClose() {
        CameraViewController.restore();
        if (isRemote() && remoteXvrPos != null) CctvNetwork.sendRemoteLogout(remoteXvrPos);
        fallbackRenderer.close();
        lastNativeCaptureCameraPos = null;
        nativeFeedLooksValid = false;
        if (feedTexture != null) {
            Minecraft.getInstance().getTextureManager().release(FEED_TEXTURE_LOCATION);
            feedTexture = null;
        }
        super.onClose();
    }

    private void drawNoCameraPattern(GuiGraphics g, int left, int top, int right, int bottom) {
        long tick = System.currentTimeMillis() / 90L;
        g.fill(left, top, right, bottom, 0xFF090C10);
        for (int y = top; y < bottom; y += 6) {
            int shade = ((y / 6 + tick) % 3 == 0) ? 0xFF252B31 : 0xFF11161B;
            g.fill(left, y, right, Math.min(bottom, y + 2), shade);
        }
    }

    private List<LivingEntity> targets() {
        Minecraft mc = Minecraft.getInstance();
        if (!alarm || mc.level == null || !signal()) return List.of();
        BlockPos camera = currentCameraPos();
        if (camera == null) return List.of();
        AABB area = new AABB(camera).inflate(14.0D);
        Player self = mc.player;
        return mc.level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity != self && entity.distanceToSqr(camera.getX() + 0.5, camera.getY() + 0.5, camera.getZ() + 0.5) <= 196.0D);
    }

    private void drawTargets(GuiGraphics g, List<LivingEntity> targets, BlockPos cameraPos, int viewX, int viewY, int viewW, int viewH) {
        Vec3 origin = cameraOrigin(cameraPos);
        Vec3 forward = forward(cameraPos);
        Vec3 right = right(cameraPos);
        Vec3 up = cameraUp(cameraPos);
        int count = 0;
        for (LivingEntity entity : targets) {
            Vec3 center = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).subtract(origin);
            double depth = center.dot(forward);
            if (depth <= 0.5 || depth > 28) continue;
            double horizontal = center.dot(right);
            double vertical = center.dot(up);
            double nx = 0.5 + horizontal / (2.0 * depth * H_FOV_TAN);
            double ny = 0.5 - vertical / (2.0 * depth * V_FOV_TAN);
            if (nx < -0.05 || nx > 1.05 || ny < -0.05 || ny > 1.05) continue;
            int cx = viewX + (int) (nx * viewW);
            int cy = viewY + (int) (ny * viewH);
            int boxW = Math.max(8, (int) (entity.getBbWidth() * viewW / (depth * 2.0 * H_FOV_TAN)));
            int boxH = Math.max(12, (int) (entity.getBbHeight() * viewH / (depth * 2.0 * V_FOV_TAN)));
            int x1 = cx - boxW / 2, y1 = cy - boxH;
            int c = 0xFFFF3535;
            g.fill(x1, y1, x1 + boxW, y1 + 1, c); g.fill(x1, y1 + boxH, x1 + boxW, y1 + boxH + 1, c);
            g.fill(x1, y1, x1 + 1, y1 + boxH, c); g.fill(x1 + boxW, y1, x1 + boxW + 1, y1 + boxH, c);
            double distance = Math.sqrt(entity.distanceToSqr(cameraPos.getX() + 0.5, cameraPos.getY() + 0.5, cameraPos.getZ() + 0.5));
            String label = "AI TARGET " + entity.getName().getString() + " " + String.format(java.util.Locale.ROOT, "%.1fm", distance);
            g.drawString(font, label, x1, Math.max(viewY + 20, y1 - 10), c, false);
            if (++count >= 8) break;
        }
    }

    private void drawCrosshair(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 13, y, x - 4, y + 1, color);
        g.fill(x + 4, y, x + 13, y + 1, color);
        g.fill(x, y - 13, x + 1, y - 4, color);
        g.fill(x, y + 4, x + 1, y + 13, color);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

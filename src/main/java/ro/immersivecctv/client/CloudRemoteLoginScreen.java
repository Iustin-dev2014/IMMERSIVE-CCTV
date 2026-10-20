package ro.immersivecctv.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import ro.immersivecctv.CctvNetwork;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.block.XvrBlock;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Manual IP/port Cloud Access terminal with an explicit scan for powered XVRs within 100 blocks. */
public final class CloudRemoteLoginScreen extends Screen {
    private BlockPos selectedXvrPos;
    private EditBox hostField;
    private EditBox portField;
    private EditBox username;
    private EditBox password;
    private String status = "Enter the XVR IP and port, or use SCAN below.";
    private boolean submitting;
    private final List<BlockPos> scanResults = new ArrayList<>();

    public CloudRemoteLoginScreen() {
        this(null);
    }

    public CloudRemoteLoginScreen(BlockPos xvrPos) {
        super(Component.literal("IMMERSIVE CCTV // CLOUD ACCESS"));
        this.selectedXvrPos = xvrPos == null ? null : xvrPos.immutable();
    }

    @Override
    protected void init() {
        int fieldW = Math.min(250, Math.max(150, width - 32));
        int left = (width - fieldW) / 2;
        int top = Math.max(18, height / 2 - 112);
        hostField = new EditBox(font, left, top + 32, fieldW, 18, Component.literal("Host IP"));
        hostField.setMaxLength(64);
        hostField.setHint(Component.literal("XVR IP address"));
        portField = new EditBox(font, left, top + 54, fieldW, 18, Component.literal("Port"));
        portField.setMaxLength(5);
        portField.setHint(Component.literal("Port"));
        username = new EditBox(font, left, top + 78, fieldW, 18, Component.literal("XVR username"));
        username.setMaxLength(32);
        username.setValue("admin");
        password = new EditBox(font, left, top + 100, fieldW, 18, Component.literal("XVR password"));
        password.setMaxLength(64);
        password.setHint(Component.literal("XVR administrator password"));
        addRenderableWidget(hostField);
        addRenderableWidget(portField);
        addRenderableWidget(username);
        addRenderableWidget(password);
        if (selectedXvrPos != null) {
            XvrBlockEntity selected = xvrAt(selectedXvrPos);
            if (selected != null) {
                hostField.setValue(selected.getCloudIp());
                portField.setValue(selected.getCloudPort());
            }
        }
        addRenderableWidget(Button.builder(Component.literal(submitting ? "CONNECTING..." : "CONNECT"), b -> connect())
                .bounds(width / 2 - 78, top + 124, 156, 18).build());
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> onClose())
                .bounds(width / 2 - 78, top + 146, 156, 18).build());

        // Results are selectable buttons, with SCAN kept at the bottom of the panel.
        int listY = Math.max(top + 168, height / 2 + 64);
        int shownCount = Math.min(scanResults.size(), Math.max(0, (height - listY - 54) / 20));
        for (int i = 0; i < shownCount; i++) {
            final BlockPos candidatePos = scanResults.get(i).immutable();
            XvrBlockEntity candidate = xvrAt(candidatePos);
            if (candidate == null) continue;
            String label = candidate.getCloudIp() + ":" + candidate.getCloudPort()
                    + " @ " + candidatePos.getX() + "," + candidatePos.getY() + "," + candidatePos.getZ();
            Button select = Button.builder(Component.literal(label), b -> {
                selectedXvrPos = candidatePos;
                XvrBlockEntity chosen = xvrAt(candidatePos);
                if (chosen != null) {
                    hostField.setValue(chosen.getCloudIp());
                    portField.setValue(chosen.getCloudPort());
                    status = "Selected powered XVR. Enter credentials and connect.";
                }
                rebuildWidgets();
            }).bounds(Math.max(4, width / 2 - 145), listY + i * 20, Math.min(290, width - 8), 18).build();
            select.active = !candidatePos.equals(selectedXvrPos);
            addRenderableWidget(select);
        }
        addRenderableWidget(Button.builder(Component.literal("SCAN"), b -> scanNearby())
                .bounds(width / 2 - 78, height - 30, 156, 18).build());
        setInitialFocus(hostField);
    }

    private XvrBlockEntity xvrAt(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null || pos == null || !level.getBlockState(pos).is(ImmersiveCCTV.XVR.get())) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof XvrBlockEntity xvr ? xvr : null;
    }

    private boolean isPowered(XvrBlockEntity xvr) {
        Level level = Minecraft.getInstance().level;
        if (level == null || xvr == null) return false;
        var state = level.getBlockState(xvr.getBlockPos());
        return state.hasProperty(XvrBlock.POWERED) && state.getValue(XvrBlock.POWERED);
    }

    private void scanNearby() {
        Level level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().player == null) {
            status = "Host unreachable. No loaded world is available.";
            return;
        }
        scanResults.clear();
        BlockPos origin = Minecraft.getInstance().player.blockPosition();
        int minChunkX = (origin.getX() - 100) >> 4;
        int maxChunkX = (origin.getX() + 100) >> 4;
        int minChunkZ = (origin.getZ() - 100) >> 4;
        int maxChunkZ = (origin.getZ() + 100) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof XvrBlockEntity xvr)) continue;
                    BlockPos pos = xvr.getBlockPos();
                    if (pos.distSqr(origin) > 10000.0D || !isPowered(xvr) || !xvr.canObtainCloudEndpoint()) continue;
                    scanResults.add(pos.immutable());
                }
            }
        }
        scanResults.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        if (scanResults.isEmpty()) {
            selectedXvrPos = null;
            status = "HOST UNREACHABLE — no powered, network-connected XVR within 100 blocks.";
        } else {
            selectedXvrPos = scanResults.get(0);
            XvrBlockEntity first = xvrAt(selectedXvrPos);
            if (first != null) {
                hostField.setValue(first.getCloudIp());
                portField.setValue(first.getCloudPort());
            }
            status = "Found " + scanResults.size() + " powered XVR(s). Select one below.";
        }
        rebuildWidgets();
    }

    private BlockPos resolveEndpoint(String ip, String port) {
        Level level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().player == null) return null;
        BlockPos origin = Minecraft.getInstance().player.blockPosition();
        int minChunkX = (origin.getX() - 100) >> 4;
        int maxChunkX = (origin.getX() + 100) >> 4;
        int minChunkZ = (origin.getZ() - 100) >> 4;
        int maxChunkZ = (origin.getZ() + 100) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof XvrBlockEntity xvr)) continue;
                    BlockPos pos = xvr.getBlockPos();
                    if (pos.distSqr(origin) > 10000.0D || !isPowered(xvr) || !xvr.canObtainCloudEndpoint()) continue;
                    if (ip.equals(xvr.getCloudIp()) && port.equals(xvr.getCloudPort())) return pos.immutable();
                }
            }
        }
        return null;
    }

    private void connect() {
        if (submitting) return;
        String ip = hostField.getValue().trim();
        String port = portField.getValue().trim();
        if (ip.isBlank() || port.isBlank()) {
            status = "Enter both the XVR IP and port.";
            return;
        }
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException ex) {
            status = "HOST UNREACHABLE — invalid port.";
            return;
        }
        if (portNumber < 1 || portNumber > 65535) {
            status = "HOST UNREACHABLE — invalid port.";
            return;
        }
        BlockPos target = resolveEndpoint(ip, port);
        if (target == null) {
            selectedXvrPos = null;
            status = "HOST UNREACHABLE";
            return;
        }
        XvrBlockEntity targetXvr = xvrAt(target);
        if (targetXvr == null || !isPowered(targetXvr) || !targetXvr.canObtainCloudEndpoint()) {
            status = "HOST UNREACHABLE";
            return;
        }
        String user = username.getValue().trim();
        String pass = password.getValue();
        if (user.length() < 3 || pass.isEmpty()) {
            status = "Enter the XVR username and password.";
            return;
        }
        selectedXvrPos = target.immutable();
        submitting = true;
        status = "Connecting...";
        rebuildWidgets();
        CctvNetwork.sendCloudLogin(target, user, pass);
    }

    public void showResult(boolean success, String message) {
        status = message == null ? "Connection failed." : message;
        submitting = false;
        if (success && selectedXvrPos != null) Minecraft.getInstance().setScreen(new XvrScreen(selectedXvrPos, true));
        else rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelW = Math.min(360, width - 16);
        int left = (width - panelW) / 2;
        int top = Math.max(8, height / 2 - 122);
        g.fill(left, top, left + panelW, height - 8, 0xF008141B);
        g.fill(left, top, left + panelW, top + 3, 0xFF43D9C7);
        g.drawCenteredString(font, "IMMERSIVE CCTV // CLOUD ACCESS", width / 2, top + 12, 0xFF71F4E2);
        g.drawCenteredString(font, "MANUAL HOST / PORT  •  SCAN RADIUS: 100 BLOCKS", width / 2, top + 22, 0xFFB8CBD0);
        super.render(g, mouseX, mouseY, partialTick);
        int maxWidth = panelW - 16;
        String shown = status;
        while (font.width(shown) > maxWidth && shown.length() > 5) shown = shown.substring(0, shown.length() - 4) + "...";
        g.drawCenteredString(font, shown, width / 2, height - 43, status.toUpperCase(java.util.Locale.ROOT).contains("UNREACHABLE") ? 0xFFFF5555 : 0xFFFFD778);


    }
}
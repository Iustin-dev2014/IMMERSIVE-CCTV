package ro.immersivecctv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ro.immersivecctv.CctvNetwork;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Credentials form for the simulated in-game cloud endpoint. */
public final class CloudRemoteLoginScreen extends Screen {
    private final BlockPos xvrPos;
    private EditBox username;
    private EditBox password;
    private String status = "Enter the administrator credentials created on first boot.";
    private boolean submitting;

    public CloudRemoteLoginScreen(BlockPos xvrPos) {
        super(Component.literal("IMMERSIVE CCTV // CLOUD ACCESS"));
        this.xvrPos = xvrPos.immutable();
    }

    @Override
    protected void init() {
        int fieldW = Math.min(250, Math.max(160, width - 36));
        int left = (width - fieldW) / 2;
        username = new EditBox(font, left, height / 2 - 12, fieldW, 20, Component.literal("XVR username"));
        username.setMaxLength(32);
        username.setValue("admin");
        password = new EditBox(font, left, height / 2 + 16, fieldW, 20, Component.literal("XVR password"));
        password.setMaxLength(64);
        password.setHint(Component.literal("XVR administrator password"));
        addRenderableWidget(username);
        addRenderableWidget(password);
        boolean endpoint = endpointAvailable();
        Button connect = Button.builder(Component.literal(submitting ? "CONNECTING..." : "CONNECT TO XVR"), b -> connect())
                .bounds(width / 2 - 90, height / 2 + 48, 180, 20).build();
        connect.active = endpoint && !submitting;
        addRenderableWidget(connect);
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> onClose())
                .bounds(width / 2 - 90, height / 2 + 73, 180, 20).build());
        setInitialFocus(username);
    }

    private XvrBlockEntity xvr() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(xvrPos);
        return be instanceof XvrBlockEntity recorder ? recorder : null;
    }

    private boolean endpointAvailable() {
        XvrBlockEntity xvr = xvr();
        return xvr != null && xvr.canObtainCloudEndpoint();
    }

    private void connect() {
        if (submitting) return;
        XvrBlockEntity xvr = xvr();
        if (xvr == null || !endpointAvailable()) {
            status = "Host unreachable. Check router power, WAN and fibre uplink.";
            submitting = false;
            return;
        }
        String user = username.getValue().trim();
        String pass = password.getValue();
        if (user.length() < 3 || pass.isEmpty()) {
            status = "Enter the XVR username and password.";
            return;
        }
        status = "Authenticating with the XVR...";
        submitting = true;
        CctvNetwork.sendCloudLogin(xvrPos, user, pass);
        clearWidgets();
        init();
    }

    public void showResult(boolean success, String message) {
        status = message == null ? "Connection failed." : message;
        submitting = false;
        if (success) Minecraft.getInstance().setScreen(new XvrScreen(xvrPos, true));
        else rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelW = Math.min(340, width - 20);
        int left = (width - panelW) / 2;
        int top = height / 2 - 105;
        g.fill(left, top, left + panelW, height / 2 + 106, 0xF008141B);
        g.fill(left, top, left + panelW, top + 3, 0xFF43D9C7);
        g.drawCenteredString(font, "IMMERSIVE CCTV // CLOUD ACCESS", width / 2, top + 13, 0xFF71F4E2);
        XvrBlockEntity xvr = xvr();
        String ip = xvr == null ? "Cannot obtain!" : xvr.getCloudIp();
        String port = xvr == null ? "Cannot obtain!" : xvr.getCloudPort();
        boolean endpoint = endpointAvailable();
        g.drawCenteredString(font, "HOST: " + ip + "   PORT: " + port, width / 2, top + 32, endpoint ? 0xFF6AFF9A : 0xFFFF5555);
        g.drawCenteredString(font, endpoint ? "SIMULATED NETWORK ROUTE AVAILABLE" : "HOST UNREACHABLE", width / 2, top + 48, endpoint ? 0xFFB8CBD0 : 0xFFFF5555);
        super.render(g, mouseX, mouseY, partialTick);
        int maxWidth = Math.min(panelW - 10, font.width(status));
        String shown = status;
        while (font.width(shown) > maxWidth && shown.length() > 4) shown = shown.substring(0, shown.length() - 4) + "...";
        g.drawCenteredString(font, shown, width / 2, height / 2 + 100, status.startsWith("Host unreachable") ? 0xFFFF5555 : 0xFFFFD778);
    }
}

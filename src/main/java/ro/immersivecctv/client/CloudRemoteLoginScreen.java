package ro.immersivecctv.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import ro.immersivecctv.CctvNetwork;

/**
 * Stand-alone Cloud Access terminal: manually enter an in-world XVR endpoint or scan nearby devices.
 * The network is simulated by the mod and is not a real TCP/IP client for public Internet servers.
 */
public final class CloudRemoteLoginScreen extends Screen {
    private EditBox hostField;
    private EditBox portField;
    private EditBox usernameField;
    private EditBox passwordField;
    private Button connectButton;
    private String savedHost = "";
    private String savedPort = "";
    private String savedUsername = "admin";
    private String savedPassword = "";
    private String status = "Enter the XVR IP and port, or use SCAN.";
    private boolean submitting;
    private boolean scanning;
    private int page;
    private List<CctvNetwork.ScanEntry> entries = List.of();

    public CloudRemoteLoginScreen() {
        super(Component.literal("IMMERSIVE CCTV // CLOUD ACCESS"));
    }

    @Override
    protected void init() {
        int panelW = Math.min(390, Math.max(220, width - 12));
        int left = (width - panelW) / 2;
        int fieldW = panelW - 20;
        int hostW = Math.max(100, (fieldW - 6) * 68 / 100);
        int portW = Math.max(66, fieldW - hostW - 6);
        int y = 31;

        hostField = new EditBox(font, left + 10, y, hostW, 18, Component.literal("IP address"));
        hostField.setMaxLength(64);
        hostField.setHint(Component.literal("XVR IP address (10.x.x.x)"));
        hostField.setValue(savedHost);
        portField = new EditBox(font, left + 10 + hostW + 6, y, portW, 18, Component.literal("Port"));
        portField.setMaxLength(8);
        portField.setHint(Component.literal("Port"));
        portField.setValue(savedPort);
        usernameField = new EditBox(font, left + 10, y + 22, fieldW, 18, Component.literal("Username"));
        usernameField.setMaxLength(32);
        usernameField.setHint(Component.literal("XVR administrator username"));
        usernameField.setValue(savedUsername);
        passwordField = new EditBox(font, left + 10, y + 44, fieldW, 18, Component.literal("Password"));
        passwordField.setMaxLength(64);
        passwordField.setHint(Component.literal("XVR administrator password"));
        passwordField.setValue(savedPassword);
        addRenderableWidget(hostField);
        addRenderableWidget(portField);
        addRenderableWidget(usernameField);
        addRenderableWidget(passwordField);

        connectButton = Button.builder(Component.literal(submitting ? "CONNECTING..." : "CONNECT"),
                button -> connect()).bounds(left + 10, y + 67, Math.max(72, (fieldW - 6) / 2), 20).build();
        connectButton.active = !submitting && !scanning;
        addRenderableWidget(connectButton);
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> onClose())
                .bounds(left + 10 + Math.max(72, (fieldW - 6) / 2) + 6, y + 67,
                        Math.max(70, fieldW - Math.max(72, (fieldW - 6) / 2) - 6), 20).build());

        int footerY = Math.max(y + 111, height - 49);
        int listY = y + 112;
        int visibleRows = Math.max(1, Math.min(6, (footerY - listY) / 19));
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) visibleRows));
        page = Math.max(0, Math.min(page, pages - 1));
        if (entries.size() > visibleRows) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                saveFields();
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(left + 10, y + 91, 26, 17).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                saveFields();
                page = Math.min(pages - 1, page + 1);
                rebuildWidgets();
            }).bounds(left + 40, y + 91, 26, 17).build());
        }

        for (int i = 0; i < visibleRows; i++) {
            int index = page * visibleRows + i;
            if (index >= entries.size()) break;
            CctvNetwork.ScanEntry entry = entries.get(index);
            String label = compactEndpoint(entry);
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                saveFields();
                savedHost = entry.host();
                savedPort = entry.port();
                status = entry.reachable()
                        ? "Selected XVR. Check credentials and press CONNECT."
                        : "HOST UNREACHABLE — selected XVR is off or its network route is down.";
                rebuildWidgets();
            }).bounds(left + 10, listY + i * 19, fieldW, 18).build());
        }

        int buttonW = Math.max(80, Math.min(140, fieldW / 2));
        addRenderableWidget(Button.builder(Component.literal(scanning ? "SCANNING..." : "SCAN"),
                button -> requestScan()).bounds(left + 10, height - 24, buttonW, 20).build());
        if (entries.size() > visibleRows) {
            addRenderableWidget(Button.builder(Component.literal("PAGE " + (page + 1) + "/" + pages), button -> {
                saveFields();
                page = (page + 1) % pages;
                rebuildWidgets();
            }).bounds(left + 16 + buttonW, height - 24, Math.max(70, fieldW - buttonW - 6), 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("CLEAR SCAN"), button -> {
                saveFields();
                entries = List.of();
                page = 0;
                status = "Scan list cleared.";
                rebuildWidgets();
            }).bounds(left + 16 + buttonW, height - 24, Math.max(70, fieldW - buttonW - 6), 20).build());
        }
        setInitialFocus(hostField);
    }

    private String compactEndpoint(CctvNetwork.ScanEntry entry) {
        String state = entry.reachable() ? "ONLINE" : "OFFLINE";
        return entry.host() + ":" + entry.port() + " [" + state + "]  " + entry.pos().getX() + " "
                + entry.pos().getY() + " " + entry.pos().getZ();
    }

    private void saveFields() {
        if (hostField != null) savedHost = hostField.getValue().trim();
        if (portField != null) savedPort = portField.getValue().trim();
        if (usernameField != null) savedUsername = usernameField.getValue().trim();
        if (passwordField != null) savedPassword = passwordField.getValue();
    }

    private void connect() {
        if (submitting || scanning) return;
        saveFields();
        if (savedHost.isEmpty() || savedPort.isEmpty()) {
            status = "Enter an IP address and port, or select an XVR returned by SCAN.";
            return;
        }
        try {
            int port = Integer.parseInt(savedPort);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            status = "Invalid port. Enter a number from 1 to 65535.";
            return;
        }
        if (savedUsername.length() < 3 || savedPassword.isEmpty()) {
            status = "Enter the XVR username and password.";
            return;
        }
        status = "CONNECTING...";
        submitting = true;
        if (connectButton != null) connectButton.active = false;
        CctvNetwork.sendCloudEndpointLogin(savedHost, savedPort, savedUsername, savedPassword);
    }

    private void requestScan() {
        if (scanning) return;
        saveFields();
        entries = List.of();
        page = 0;
        scanning = true;
        status = "Scanning loaded XVRs within 100 blocks...";
        rebuildWidgets();
        CctvNetwork.requestCloudScan();
    }

    public void showResult(boolean success, BlockPos resolvedXvrPos, String message) {
        submitting = false;
        status = message == null ? "Connection failed." : message;
        if (success && resolvedXvrPos != null && !resolvedXvrPos.equals(BlockPos.ZERO)) {
            Minecraft.getInstance().setScreen(new XvrScreen(resolvedXvrPos, true));
        } else {
            if (connectButton != null) connectButton.active = !scanning;
        }
    }

    public void showScanResults(List<CctvNetwork.ScanEntry> found) {
        saveFields();
        entries = found == null ? List.of() : List.copyOf(found);
        scanning = false;
        page = 0;
        status = entries.isEmpty()
                ? "No XVRs found in the 100-block radius. Only loaded chunks are scanned."
                : "Scan found " + entries.size() + " XVR(s). Choose a row, then press CONNECT.";
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelW = Math.min(390, Math.max(220, width - 12));
        int left = (width - panelW) / 2;
        g.fill(left, 5, left + panelW, height - 4, 0xF008141B);
        g.fill(left, 5, left + panelW, 8, 0xFF43D9C7);
        g.drawCenteredString(font, "IMMERSIVE CCTV // CLOUD ACCESS", width / 2, 13, 0xFF71F4E2);
        g.drawCenteredString(font, "SIMULATED IN-WORLD NETWORK • NOT PUBLIC INTERNET", width / 2, 22, 0xFF8DA5AD);
        g.drawString(font, "HOST / IP", left + 10, 25, 0xFFB8CBD0, false);
        g.drawString(font, "PORT", left + 10 + Math.max(100, (panelW - 26) * 68 / 100) + 6, 25, 0xFFB8CBD0, false);
        g.drawString(font, "USERNAME", left + 10, 47, 0xFFB8CBD0, false);
        g.drawString(font, "PASSWORD", left + 10, 69, 0xFFB8CBD0, false);
        int statusY = Math.min(height - 47, 119);
        String shown = status;
        int available = panelW - 18;
        while (font.width(shown) > available && shown.length() > 8) shown = shown.substring(0, shown.length() - 4) + "...";
        int statusColor = status.toUpperCase(java.util.Locale.ROOT).contains("UNREACHABLE")
                || status.toUpperCase(java.util.Locale.ROOT).startsWith("LOGIN FAILED")
                ? 0xFFFF6262 : (submitting || scanning ? 0xFFFFD778 : 0xFFB4CFD5);
        g.drawCenteredString(font, shown, width / 2, statusY, statusColor);
        if (entries.isEmpty()) {
            g.drawCenteredString(font, "SCAN DISCOVERS XVRs WITHIN 100 BLOCKS", width / 2, Math.max(statusY + 13, height - 45), 0xFF81959C);
        } else {
            int listY = 143;
            int footerY = Math.max(143, height - 49);
            int visibleRows = Math.max(1, Math.min(6, (footerY - listY) / 19));
            int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) visibleRows));
            g.drawCenteredString(font, "DISCOVERED XVRs  •  PAGE " + (Math.min(page, pages - 1) + 1) + "/" + pages,
                    width / 2, statusY + 14, 0xFF43D9C7);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (submitting) submitting = false;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
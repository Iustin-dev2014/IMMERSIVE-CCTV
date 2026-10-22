package com.immersivecctv;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Full screen XVR interface: live view with the mouse, plus the real settings menu. */
public class XvrScreen extends Screen {
    private static final String[] TAB_NAMES = {"Display", "Channels", "Record", "Time", "Network", "System"};
    private static final int TOOLBAR_H = 26;

    private final BlockPos monitorPos;
    private int tab = -1;          // -1 = live view, -2 = PIN prompt, 0..5 = menu tabs
    private int selCh = 0;
    private int focus = -1;
    private boolean unlocked = false;
    private boolean wrongPin = false;
    private boolean confirmReset = false;
    private String usedPin = "";
    private int lastHash = 0;

    private String nameText = null;
    private String devText = null;
    private String pinText = "";
    private String newPinText = "";

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int c1;
    private int c2;
    private int cw;
    private int cy;
    private final int rh = 24;

    public XvrScreen(BlockPos monitorPos) {
        super(Component.literal("XVR"));
        this.monitorPos = monitorPos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private XvrBlockEntity xvr() {
        Level lv = Minecraft.getInstance().level;
        if (lv == null) {
            return null;
        }
        BlockEntity be = lv.getBlockEntity(monitorPos);
        if (!(be instanceof MonitorBlockEntity)) {
            return null;
        }
        BlockPos xp = ((MonitorBlockEntity) be).getXvrPos();
        if (xp == null) {
            return null;
        }
        BlockEntity xb = lv.getBlockEntity(xp);
        return xb instanceof XvrBlockEntity ? (XvrBlockEntity) xb : null;
    }

    private boolean needsPin(XvrBlockEntity x) {
        return x != null && !x.getPin().isEmpty() && !unlocked;
    }

    private void send(int action, int arg, String text) {
        XvrBlockEntity x = xvr();
        if (x == null) {
            return;
        }
        Net.CHANNEL.sendToServer(new XvrActionPacket(x.getBlockPos(), action, arg,
                text == null ? "" : text, usedPin));
    }

    /** Runs a protected action, asking for the PIN first if one is set. */
    private void secure(Runnable r) {
        if (needsPin(xvr())) {
            tab = -2;
            rebuildWidgets();
        } else {
            r.run();
        }
    }

    private Button btn(int x, int y, int w, String label, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> r.run())
                .bounds(x, y, w, 20).build());
    }

    private EditBox edit(int x, int y, int w, String value, int max, Consumer<String> responder) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal(""));
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        box.setResponder(responder);
        addRenderableWidget(box);
        return box;
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private static String layoutName(int n) {
        return n == 1 ? "1x1" : n == 4 ? "2x2" : n == 9 ? "3x3" : "4x4";
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init() {
        XvrBlockEntity x = xvr();
        if (x != null) {
            lastHash = x.settingsHash();
            if (!x.isPowered() && tab != -1) {
                tab = -1;
            }
        }
        toolbar(x);
        if (tab == -2) {
            buildPin();
        } else if (tab >= 0 && x != null) {
            buildMenu(x);
        }
    }

    private void toolbar(XvrBlockEntity x) {
        int y = height - TOOLBAR_H + 3;
        int bx = 6;
        btn(bx, y, 74, tab == -1 ? "Menu" : "Live view", () -> {
            if (tab == -1) {
                tab = needsPin(xvr()) ? -2 : 0;
            } else {
                tab = -1;
            }
            rebuildWidgets();
        });
        bx += 78;
        int cur = x == null ? 4 : x.getLayout();
        btn(bx, y, 92, "Layout: " + layoutName(cur), () -> {
            int next = cur == 1 ? 4 : cur == 4 ? 9 : cur == 9 ? 16 : 1;
            focus = -1;
            send(XvrBlockEntity.A_LAYOUT, next, null);
        });
        bx += 96;
        btn(bx, y, 24, "<", () -> nav(-1));
        bx += 28;
        btn(bx, y, 24, ">", () -> nav(1));
        bx += 28;
        btn(bx, y, 78, "Shutdown", () -> secure(() -> send(XvrBlockEntity.A_SHUTDOWN, 0, null)));
        btn(width - 60, y, 54, "Close", this::onClose);
    }

    private void nav(int delta) {
        if (focus >= 0) {
            focus = (focus + delta + XvrBlockEntity.CHANNELS) % XvrBlockEntity.CHANNELS;
        } else {
            send(XvrBlockEntity.A_PAGE, delta, null);
        }
    }

    private void buildPin() {
        int bx = width / 2 - 90;
        int by = height / 2 - 20;
        edit(bx, by, 180, pinText, 8, s -> pinText = s).setFilter(s -> s.matches("[0-9]*"));
        btn(bx, by + 26, 180, "Unlock", () -> {
            XvrBlockEntity x = xvr();
            if (x != null && x.getPin().equals(pinText)) {
                unlocked = true;
                usedPin = pinText;
                pinText = "";
                wrongPin = false;
                tab = 0;
            } else {
                wrongPin = true;
            }
            rebuildWidgets();
        });
    }

    private void buildMenu(XvrBlockEntity x) {
        px = 20;
        py = 10;
        pw = width - 40;
        ph = height - TOOLBAR_H - 16;
        int tabW = (pw - 16 - 5 * 4) / 6;
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            btn(px + 8 + i * (tabW + 4), py + 6, tabW, (i == tab ? "> " : "") + TAB_NAMES[i], () -> {
                tab = idx;
                confirmReset = false;
                rebuildWidgets();
            });
        }
        cw = Math.min(190, (pw - 40) / 2);
        c1 = px + 14;
        c2 = px + 14 + cw + 12;
        cy = py + 36;
        switch (tab) {
            case 0:
                displayTab(x);
                break;
            case 1:
                channelsTab(x);
                break;
            case 2:
                recordTab(x);
                break;
            case 3:
                timeTab(x);
                break;
            case 4:
                networkTab(x);
                break;
            default:
                systemTab(x);
                break;
        }
    }

    private void displayTab(XvrBlockEntity x) {
        btn(c1, cy, cw, "OSD channel name: " + onOff(x.isOsdName()),
                () -> send(XvrBlockEntity.A_OSD_NAME, 0, null));
        btn(c2, cy, cw, "OSD date/time: " + onOff(x.isOsdTime()),
                () -> send(XvrBlockEntity.A_OSD_TIME, 0, null));
        btn(c1, cy + rh, cw, "REC indicator: " + onOff(x.isRecInd()),
                () -> send(XvrBlockEntity.A_REC_IND, 0, null));
        btn(c2, cy + rh, cw, "Layout: " + layoutName(x.getLayout()), () -> {
            int cur = x.getLayout();
            send(XvrBlockEntity.A_LAYOUT, cur == 1 ? 4 : cur == 4 ? 9 : cur == 9 ? 16 : 1, null);
        });
        btn(c1, cy + 2 * rh, cw, "Brightness -  (" + x.getBrightness() + "%)",
                () -> send(XvrBlockEntity.A_BRIGHT, -10, null));
        btn(c2, cy + 2 * rh, cw, "Brightness +",
                () -> send(XvrBlockEntity.A_BRIGHT, 10, null));
        btn(c1, cy + 3 * rh, cw, "Live camera render: " + onOff(ClientPrefs.liveRender), () -> {
            ClientPrefs.liveRender = !ClientPrefs.liveRender;
            rebuildWidgets();
        });
        btn(c2, cy + 3 * rh, cw, "Quality: " + ClientPrefs.qualityName(), () -> {
            ClientPrefs.quality = (ClientPrefs.quality + 1) % 3;
            rebuildWidgets();
        });
        btn(c1, cy + 4 * rh, cw, "Feed rate: " + ClientPrefs.fps() + " renders/s", () -> {
            ClientPrefs.fpsIndex = (ClientPrefs.fpsIndex + 1) % 3;
            rebuildWidgets();
        });
    }

    private void channelsTab(XvrBlockEntity x) {
        btn(c1, cy, cw, "< Previous channel", () -> {
            selCh = (selCh + XvrBlockEntity.CHANNELS - 1) % XvrBlockEntity.CHANNELS;
            nameText = null;
            rebuildWidgets();
        });
        btn(c2, cy, cw, "Next channel >", () -> {
            selCh = (selCh + 1) % XvrBlockEntity.CHANNELS;
            nameText = null;
            rebuildWidgets();
        });
        if (nameText == null) {
            nameText = x.getRawChannelName(selCh);
        }
        edit(c1, cy + rh, cw, nameText, 20, s -> nameText = s);
        btn(c2, cy + rh, cw, "Apply name", () -> send(XvrBlockEntity.A_CH_NAME, selCh, nameText));
        btn(c1, cy + 2 * rh, cw, "Channel enabled: " + onOff(x.isChannelEnabled(selCh)),
                () -> send(XvrBlockEntity.A_CH_ENABLE, selCh, null));
        btn(c2, cy + 2 * rh, cw, "Mirror image: " + onOff(x.isChannelMirror(selCh)),
                () -> send(XvrBlockEntity.A_CH_MIRROR, selCh, null));
        btn(c1, cy + 3 * rh, cw, "Recording: " + onOff(x.isChannelRec(selCh)),
                () -> secure(() -> send(XvrBlockEntity.A_CH_REC, selCh, null)));
        btn(c2, cy + 3 * rh, cw, "View this channel", () -> {
            focus = selCh;
            tab = -1;
            rebuildWidgets();
        });
    }

    private void recordTab(XvrBlockEntity x) {
        btn(c1, cy, cw, "Recording: " + onOff(x.isRecAll()),
                () -> secure(() -> send(XvrBlockEntity.A_REC_ALL, 0, null)));
        btn(c2, cy, cw, "Overwrite when full: " + onOff(x.isOverwrite()),
                () -> secure(() -> send(XvrBlockEntity.A_OVERWRITE, 0, null)));
        btn(c1, cy + rh, cw, "Format HDD",
                () -> secure(() -> send(XvrBlockEntity.A_FORMAT, 0, null)));
    }

    private void timeTab(XvrBlockEntity x) {
        btn(c1, cy, cw, "Time source: " + (x.isTimeReal() ? "REAL CLOCK" : "GAME TIME"),
                () -> send(XvrBlockEntity.A_TIME_SRC, 0, null));
        btn(c2, cy, cw, "Date format: " + new String[]{"YYYY-MM-DD", "DD-MM-YYYY", "MM-DD-YYYY"}[x.getDateFmt() % 3],
                () -> send(XvrBlockEntity.A_DATE_FMT, 0, null));
        btn(c1, cy + rh, cw, "Time zone -  (UTC" + (x.getTzOffset() >= 0 ? "+" : "") + x.getTzOffset() + " h)",
                () -> send(XvrBlockEntity.A_TZ, -1, null));
        btn(c2, cy + rh, cw, "Time zone +",
                () -> send(XvrBlockEntity.A_TZ, 1, null));
    }

    private void networkTab(XvrBlockEntity x) {
        btn(c1, cy, cw, "PoE power to cameras: " + onOff(x.isPoe()),
                () -> secure(() -> send(XvrBlockEntity.A_POE, 0, null)));
    }

    private void systemTab(XvrBlockEntity x) {
        if (devText == null) {
            devText = x.getDeviceName();
        }
        edit(c1, cy, cw, devText, 16, s -> devText = s);
        btn(c2, cy, cw, "Apply device name", () -> secure(() -> send(XvrBlockEntity.A_DEVNAME, 0, devText)));
        btn(c1, cy + rh, cw, "Reboot", () -> secure(() -> send(XvrBlockEntity.A_REBOOT, 0, null)));
        btn(c2, cy + rh, cw, "Shutdown", () -> secure(() -> send(XvrBlockEntity.A_SHUTDOWN, 0, null)));
        edit(c1, cy + 2 * rh, cw, newPinText, 8, s -> newPinText = s).setFilter(s -> s.matches("[0-9]*"));
        btn(c2, cy + 2 * rh, cw, "Set PIN", () -> secure(() -> {
            send(XvrBlockEntity.A_PIN_SET, 0, newPinText);
            usedPin = newPinText;
            unlocked = true;
            newPinText = "";
        }));
        btn(c1, cy + 3 * rh, cw, "Clear PIN", () -> secure(() -> {
            send(XvrBlockEntity.A_PIN_SET, 0, "");
            usedPin = "";
        }));
        btn(c2, cy + 3 * rh, cw, confirmReset ? "Click again to confirm!" : "Factory reset", () -> {
            if (!confirmReset) {
                confirmReset = true;
                rebuildWidgets();
            } else {
                confirmReset = false;
                secure(() -> send(XvrBlockEntity.A_RESET, 0, null));
            }
        });
    }

    // ------------------------------------------------------------------ logic

    @Override
    public void tick() {
        XvrBlockEntity x = xvr();
        if (x != null && x.settingsHash() != lastHash) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        XvrBlockEntity x = xvr();
        if (tab == -1 && button == 0 && x != null && x.isPowered()) {
            float viewH = height - TOOLBAR_H;
            float sc = Math.min(width / XvrView.W, viewH / XvrView.H);
            float ox = (width - XvrView.W * sc) / 2F;
            float oy = (viewH - XvrView.H * sc) / 2F;
            float vx = (float) ((mx - ox) / sc);
            float vy = (float) ((my - oy) / sc);
            if (vx >= 0 && vy >= 0 && vx < XvrView.W && vy < XvrView.H) {
                if (focus >= 0) {
                    focus = -1;
                } else {
                    int n = x.getLayout();
                    int cols = n == 1 ? 1 : n == 4 ? 2 : n == 9 ? 3 : 4;
                    int idx = (int) (vy / (XvrView.H / cols)) * cols + (int) (vx / (XvrView.W / cols));
                    int ch = x.getPage() * n + idx;
                    if (ch < XvrBlockEntity.CHANNELS) {
                        focus = ch;
                    }
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF000000);
        Level lv = Minecraft.getInstance().level;
        XvrBlockEntity x = xvr();
        if (lv != null) {
            float viewH = height - TOOLBAR_H;
            float sc = Math.min(width / XvrView.W, viewH / XvrView.H);
            float ox = (width - XvrView.W * sc) / 2F;
            float oy = (viewH - XvrView.H * sc) / 2F;
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(ox, oy, 0F);
            ps.scale(sc, sc, sc);
            ScreenCanvas c = new ScreenCanvas(this.font);
            float t = lv.getGameTime() + pt;
            if (x == null || !x.isPowered()) {
                XvrView.noSignal(c);
            } else {
                XvrView.draw(c, x, lv, t, focus, true);
            }
            c.flush(ps, g.bufferSource());
            ps.popPose();
            g.flush();
        }
        if (tab >= 0 && x != null) {
            g.fill(0, 0, width, height - TOOLBAR_H, 0xB0000000);
            g.fill(px, py, px + pw, py + ph, 0xF0101820);
            g.fill(px, py, px + pw, py + 1, 0xFF3A7BD5);
            g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF3A7BD5);
        } else if (tab == -2) {
            g.fill(0, 0, width, height - TOOLBAR_H, 0xD0000000);
        }
        super.render(g, mx, my, pt);
        if (tab == -2) {
            g.drawString(this.font, "Enter the XVR PIN", width / 2 - 50, height / 2 - 38, 0xFFFFFFFF, false);
            if (wrongPin) {
                g.drawString(this.font, "Wrong PIN", width / 2 - 24, height / 2 + 34, 0xFFFF5555, false);
            }
        } else if (tab >= 0 && x != null) {
            info(g, x);
        }
        if (tab == -1 && focus >= 0) {
            g.drawString(this.font, "Click the picture to go back to the grid", 6, 4, 0xFFFFFFFF, true);
        }
    }

    private void line(GuiGraphics g, String s, int x, int y) {
        g.drawString(this.font, s, x, y, 0xFFE0E6F0, false);
    }

    private void info(GuiGraphics g, XvrBlockEntity x) {
        int iy = cy + 5 * rh;
        switch (tab) {
            case 1: {
                int ch = selCh;
                line(g, "Channel " + (ch + 1) + " of 16   IP 192.168.1." + (101 + ch), c1, cy + 4 * rh + 4);
                String st = x.isOnline(ch) ? "ONLINE" : x.getCamera(ch) != null ? "OFFLINE" : "NO CAMERA";
                line(g, "Status: " + st + "   (LAN port " + (ch + 1) + ")", c1, cy + 4 * rh + 18);
                break;
            }
            case 2: {
                long used = x.getHddUsedMb();
                int pct = (int) (used * 100L / XvrBlockEntity.HDD_MB);
                line(g, String.format("HDD 1 TB   used %.1f GB (%d%%)", used / 1000.0, pct), c1, cy + 2 * rh + 6);
                int bw = pw - 28;
                g.fill(c1, cy + 3 * rh, c1 + bw, cy + 3 * rh + 8, 0xFF303840);
                g.fill(c1, cy + 3 * rh, c1 + (int) (bw * used / (double) XvrBlockEntity.HDD_MB),
                        cy + 3 * rh + 8, pct > 90 ? 0xFFE05050 : 0xFF3CB46E);
                Level lv = Minecraft.getInstance().level;
                int n = x.recordingChannels();
                String rem = "-";
                if (n > 0) {
                    long sec = (XvrBlockEntity.HDD_MB - used) / (30L * n);
                    rem = String.format("%dh %02dm", sec / 3600, (sec % 3600) / 60);
                }
                boolean rec = lv != null && x.isRecordingActive(lv.getGameTime());
                line(g, "Status: " + (rec ? "RECORDING " + n + " channel(s)" : "NOT RECORDING")
                        + "    Time left: " + rem, c1, cy + 3 * rh + 16);
                break;
            }
            case 3: {
                String now = x.isTimeReal()
                        ? LocalDateTime.now().plusHours(x.getTzOffset()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : "game clock";
                line(g, "Current time: " + now, c1, cy + 2 * rh + 6);
                break;
            }
            case 4: {
                line(g, "XVR IP 192.168.1.10   Online cameras: " + x.onlineCount() + " / 16", c1, cy + rh + 4);
                for (int i = 0; i < XvrBlockEntity.CHANNELS; i++) {
                    int colx = i < 8 ? c1 : c2;
                    int rowy = cy + 2 * rh + (i % 8) * 11;
                    String st = x.isOnline(i) ? "ONLINE" : x.getCamera(i) != null ? "OFFLINE" : "-";
                    g.drawString(this.font, String.format("CH%02d  192.168.1.%d  %s", i + 1, 101 + i, st),
                            colx, rowy, x.isOnline(i) ? 0xFF66FF88 : 0xFF8890A0, false);
                }
                break;
            }
            case 5: {
                Level lv = Minecraft.getInstance().level;
                long up = lv != null && x.isPowered() ? (lv.getGameTime() - x.getPowerOnTime()) / 20L : 0L;
                line(g, "Firmware v2.31   Uptime " + up / 60 + " min " + up % 60 + " s   PIN: "
                        + (x.getPin().isEmpty() ? "not set" : "set"), c1, cy + 4 * rh + 6);
                line(g, "Device: " + x.getDeviceName() + "   Cameras: " + x.onlineCount() + "/16", c1, cy + 4 * rh + 20);
                break;
            }
            default:
                line(g, "Camera picture: the PC/phone renders the world from each camera.", c1, iy - 4);
                line(g, "Lower the quality or the feed rate if the game gets slow.", c1, iy + 8);
                break;
        }
    }
}

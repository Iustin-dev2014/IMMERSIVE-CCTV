package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Everything that appears on the monitor (in the world and in the full screen UI). Virtual size 160 x 90. */
public final class XvrView {
    public static final float W = 160F;
    public static final float H = 90F;

    private static final DateTimeFormatter[] FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss")
    };
    private static final String[] BOOT_LINES = {
            "BIOS v2.31   CPU ......... OK",
            "Memory 512MB .......... OK",
            "HDD 1TB ............... OK",
            "Loading firmware...",
            "LAN switch: 16 ports PoE",
            "Init video channels",
            "System ready"
    };
    private static final int[] BOOT_AT = {25, 45, 65, 90, 120, 150, 178};

    private XvrView() {
    }

    public static void draw(ScreenCanvas c, XvrBlockEntity xvr, Level level, float t, int focus, boolean allowLive) {
        if (!xvr.isPowered()) {
            noSignal(c);
            return;
        }
        float el = t - xvr.getPowerOnTime();
        if (el < XvrBlockEntity.BOOT_TICKS) {
            boot(c, xvr, el);
            return;
        }
        grid(c, xvr, level, t, focus, allowLive);
    }

    public static void noSignal(ScreenCanvas c) {
        c.rect(0, 0, W, H, 0F, 0xFF0A1030);
        c.textC("NO SIGNAL", W / 2F, 32F, 2.0F, 0xFFFFFFFF);
        c.textC("Check HDMI source", W / 2F, 56F, 0.9F, 0xFFAAB4D0);
    }

    private static void boot(ScreenCanvas c, XvrBlockEntity xvr, float el) {
        c.rect(0, 0, W, H, 0F, 0xFF000000);
        if (el >= 8F) {
            c.text(xvr.getDeviceName(), 8F, 5F, 1.0F, 0xFFFFFFFF);
        }
        for (int i = 0; i < BOOT_LINES.length; i++) {
            if (el >= BOOT_AT[i]) {
                c.text(BOOT_LINES[i], 8F, 17F + i * 8F, 0.85F, 0xFF66FF66);
            }
        }
        float p = Math.max(0F, Math.min(1F, (el - 20F) / 170F));
        c.text("Starting up... " + (int) (p * 100F) + "%", 10F, 74F, 0.75F, 0xFFCCCCCC);
        c.rect(10F, 82F, 150F, 87F, 0.1F, 0xFF303030);
        c.rect(11F, 83F, 11F + 138F * p, 86F, 0.2F, 0xFF33CC55);
    }

    private static String clock(XvrBlockEntity x, Level level) {
        if (x.isTimeReal()) {
            return LocalDateTime.now().plusHours(x.getTzOffset()).format(FORMATS[x.getDateFmt() % 3]);
        }
        long dt = level.getDayTime();
        long day = dt / 24000L + 1L;
        long sec = (long) ((dt % 24000L) * 3.6D + 21600D + x.getTzOffset() * 3600D);
        sec = ((sec % 86400L) + 86400L) % 86400L;
        return String.format("Day %d  %02d:%02d:%02d", day, sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    private static void grid(ScreenCanvas c, XvrBlockEntity xvr, Level level, float t, int focus,
                             boolean allowLive) {
        int n = focus >= 0 ? 1 : xvr.getLayout();
        int cols = n == 1 ? 1 : n == 4 ? 2 : n == 9 ? 3 : 4;
        float tw = W / cols;
        float th = H / cols;
        String clock = clock(xvr, level);
        boolean recActive = xvr.isRecordingActive(level.getGameTime());
        for (int idx = 0; idx < n; idx++) {
            int ch = focus >= 0 ? focus : xvr.getPage() * n + idx;
            float tx = (idx % cols) * tw;
            float ty = (idx / cols) * th;
            if (ch >= XvrBlockEntity.CHANNELS) {
                c.rect(tx, ty, tx + tw - 1F, ty + th - 1F, 0F, 0xFF050505);
                continue;
            }
            tile(c, xvr, level, t, ch, tx, ty, tw - 1F, th - 1F, clock, recActive, allowLive);
        }
    }

    private static void tile(ScreenCanvas c, XvrBlockEntity xvr, Level level, float t, int ch,
                             float tx, float ty, float w, float h, String clock, boolean recActive,
                             boolean allowLive) {
        float k = Math.max(0.45F, w / 80F);
        if (!xvr.isChannelEnabled(ch)) {
            c.rect(tx, ty, tx + w, ty + h, 0F, 0xFF050505);
            c.text(xvr.getChannelName(ch) + "  OFF", tx + 3F * k, ty + 3F * k, 0.6F * k, 0xFF808080);
            return;
        }
        if (!xvr.isOnline(ch)) {
            noiseTile(c, ch, tx, ty, w, h, t, k);
            return;
        }
        BlockPos cam = xvr.getCamera(ch);
        float since = t - Math.max(xvr.getCameraTime(ch), xvr.getPowerOnTime() + XvrBlockEntity.BOOT_TICKS);
        if (since < 80F) {
            c.rect(tx, ty, tx + w, ty + h, 0F, 0xFF0E1216);
            String dots = ".".repeat(((int) (t / 6F)) % 4);
            c.text("CONNECTING" + dots, tx + w * 0.2F, ty + h * 0.4F, 0.75F * k, 0xFFE0E0E0);
            float p = Math.max(0F, Math.min(1F, since / 80F));
            c.rect(tx + w * 0.15F, ty + h * 0.7F, tx + w * 0.85F, ty + h * 0.78F, 0.1F, 0xFF303840);
            c.rect(tx + w * 0.15F, ty + h * 0.7F, tx + w * (0.15F + 0.7F * p), ty + h * 0.78F, 0.2F, 0xFF3CB4FF);
            osdName(c, xvr, ch, tx, ty, k);
            return;
        }
        ResourceLocation feed = allowLive ? CameraFeeds.request(cam) : null;
        int v = Math.min(255, xvr.getBrightness() * 255 / 100);
        int tint = 0xFF000000 | (v << 16) | (v << 8) | v;
        if (feed != null) {
            float u0 = xvr.isChannelMirror(ch) ? 1F : 0F;
            c.image(feed, tx, ty, tx + w, ty + h, 0.02F, u0, 1F, 1F - u0, 0F, tint);
        } else {
            int[] sky = {0xFF6F7F88, 0xFF7B8B94, 0xFF8797A0, 0xFF93A3AC};
            int[] gnd = {0xFF4E524C, 0xFF43473F, 0xFF383C35, 0xFF2E322B};
            for (int i = 0; i < 4; i++) {
                c.rect(tx, ty + i * h * 0.1125F, tx + w, ty + (i + 1) * h * 0.1125F, 0F, sky[i]);
                c.rect(tx, ty + h * 0.45F + i * h * 0.1375F, tx + w, ty + h * 0.45F + (i + 1) * h * 0.1375F, 0F, gnd[i]);
            }
            c.text(allowLive ? "LOADING LIVE VIEW..." : "OUT OF RANGE", tx + w * 0.12F, ty + h * 0.45F, 0.7F * k, 0xFFFFFFFF);
        }
        osdName(c, xvr, ch, tx, ty, k);
        if (xvr.isOsdTime()) {
            float s = 0.55F * k;
            float tw = c.width(clock) * s;
            c.rect(tx + 2F * k, ty + h - 8F * k, tx + 2F * k + tw + 3F * k, ty + h - 2F * k, 0.1F, 0x99000000);
            c.text(clock, tx + 3.5F * k, ty + h - 7.2F * k, s, 0xFFFFFFFF);
        }
        if (xvr.isRecInd() && recActive && xvr.isChannelRec(ch)) {
            if (((int) (t / 10F)) % 2 == 0) {
                c.rect(tx + w - 17F * k, ty + 3F * k, tx + w - 13F * k, ty + 7F * k, 0.2F, 0xFFE02020);
            }
            c.text("REC", tx + w - 12F * k, ty + 2.8F * k, 0.6F * k, 0xFFFFFFFF);
        }
    }

    private static void osdName(ScreenCanvas c, XvrBlockEntity xvr, int ch, float tx, float ty, float k) {
        if (!xvr.isOsdName()) {
            return;
        }                  String name = xvr.getChannelName(ch);
        float s = 0.7F * k;
        float tw = c.width(name) * s;
        c.rect(tx + 2F * k, ty + 2F * k, tx + 2F * k + tw + 3F * k, ty + 9F * k, 0.1F, 0x99000000);
        c.text(name, tx + 3.5F * k, ty + 3F * k, s, 0xFFFFFFFF);
    }

    private static void noiseTile(ScreenCanvas c, int ch, float tx, float ty, float w, float h, float t, float k) {
        int seed = (int) (t / 2F);
        float ou = (hash(seed, ch, 1) & 255) / 255F;
        float ov = (hash(seed, ch, 2) & 255) / 255F;
        c.image(ScreenCanvas.NOISE, tx, ty, tx + w, ty + h, 0.01F, ou, ov, ou + 0.5F, ov + 0.28F, 0xFFFFFFFF);
        c.rect(tx + w * 0.15F, ty + h * 0.36F, tx + w * 0.85F, ty + h * 0.64F, 0.1F, 0xCC000000);
        c.textC("NO SIGNAL", tx + w / 2F, ty + h * 0.42F, 0.8F * k, 0xFFFF3030);
        c.rect(tx + 2F * k, ty + 2F * k, tx + 18F * k, ty + 9F * k, 0.1F, 0xAA000000);
        c.text("CH" + (ch + 1), tx + 3.5F * k, ty + 3F * k, 0.7F * k, 0xFFFFFFFF);
    }

    private static int hash(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}

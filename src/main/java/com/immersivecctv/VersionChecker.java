package com.immersivecctv;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the latest published GitHub release when the client joins a world/server.
 * This checker only notifies the player; it never downloads or installs files.
 */
@Mod.EventBusSubscriber(modid = ImmersiveCCTV.MODID, value = Dist.CLIENT)
public final class VersionChecker {
    private static final String RELEASES_API =
        "https://api.github.com/repos/Iustin-dev2014/IMMERSIVE-CCTV/releases?per_page=10";

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)(?:[-+](.+))?$");

    private VersionChecker() {}

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Thread checker = new Thread(VersionChecker::checkLatestRelease,
            "IMMERSIVE-CCTV-VersionChecker");
        checker.setDaemon(true);
        checker.start();
    }

    private static void checkLatestRelease() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(RELEASES_API).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "IMMERSIVE-CCTV-VersionChecker");

            try (InputStream stream = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonArray releases = JsonParser.parseReader(reader).getAsJsonArray();
                String current = ModList.get().getModContainerById(ImmersiveCCTV.MODID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("1.6.0");

                for (int i = 0; i < releases.size(); i++) {
                    JsonObject release = releases.get(i).getAsJsonObject();
                    if (release.get("draft").getAsBoolean()) {
                        continue;
                    }

                    String latest = release.get("tag_name").getAsString();
                    if (isNewer(latest, current)) {
                        String url = release.has("html_url")
                            ? release.get("html_url").getAsString()
                            : "https://github.com/Iustin-dev2014/IMMERSIVE-CCTV/releases";

                        Minecraft.getInstance().execute(() -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.player.displayClientMessage(
                                    Component.literal("[IMMERSIVE CCTV] New version available: " + latest)
                                        .withStyle(ChatFormatting.GOLD), false);
                                mc.player.displayClientMessage(
                                    Component.literal("Download: " + url)
                                        .withStyle(ChatFormatting.AQUA), false);
                            }
                        });
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep gameplay unaffected when offline or GitHub is temporarily unavailable.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isNewer(String remote, String local) {
        Matcher r = VERSION_PATTERN.matcher(remote.trim());
        Matcher l = VERSION_PATTERN.matcher(local.trim());
        if (!r.matches() || !l.matches()) {
            return false;
        }

        String[] remoteParts = r.group(1).split("\\.");
        String[] localParts = l.group(1).split("\\.");
        int count = Math.max(remoteParts.length, localParts.length);

        for (int i = 0; i < count; i++) {
            long rv = i < remoteParts.length ? parsePart(remoteParts[i]) : 0;
            long lv = i < localParts.length ? parsePart(localParts[i]) : 0;
            if (rv != lv) {
                return rv > lv;
            }
        }

        // For equal numeric versions, a stable release is newer than a prerelease.
        String remoteSuffix = r.group(2);
        String localSuffix = l.group(2);
        if (remoteSuffix == null && localSuffix != null) {
            return true;
        }
        if (remoteSuffix != null && localSuffix == null) {
            return false;
        }
        return remoteSuffix != null && localSuffix != null
            && remoteSuffix.compareToIgnoreCase(localSuffix) > 0;
    }

    private static long parsePart(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}

package ro.immersivecctv.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-side DVR: stores timestamped PNG frames captured from the actual CCTV feed shown to the player. */
public final class LocalRecorder {
    private static final long RECORD_INTERVAL_MS = 2500L;
    private static final int MAX_FRAMES_PER_CAMERA = 360;
    private static final ConcurrentHashMap<Path, Long> LAST_CAPTURE = new ConcurrentHashMap<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ImmersiveCCTV-Recorder");
        thread.setDaemon(true);
        return thread;
    });
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private LocalRecorder() {}

    private static Path cameraDirectory(Minecraft mc, BlockPos cameraPos) {
        String world = "world";
        try {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
                world = mc.getCurrentServer().ip;
            } else if (mc.getSingleplayerServer() != null) {
                world = mc.getSingleplayerServer().getWorldData().getLevelName();
            }
        } catch (RuntimeException ignored) { }
        world = safe(world);
        String dimension = mc.level == null ? "unknown" : safe(mc.level.dimension().location().toString());
        return mc.gameDirectory.toPath().resolve("immersive_cctv").resolve("recordings")
                .resolve(world).resolve(dimension)
                .resolve(cameraPos.getX() + "_" + cameraPos.getY() + "_" + cameraPos.getZ());
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static void recordFrame(Minecraft mc, BlockPos cameraPos, int[] rgb, int width, int height) {
        if (mc == null || mc.level == null || cameraPos == null || rgb == null || rgb.length != width * height) return;
        Path directory = cameraDirectory(mc, cameraPos);
        long now = System.currentTimeMillis();
        Long previous = LAST_CAPTURE.get(directory);
        if (previous != null && now - previous < RECORD_INTERVAL_MS) return;
        LAST_CAPTURE.put(directory, now);
        int[] snapshot = rgb.clone();
        WRITER.execute(() -> {
            try {
                Files.createDirectories(directory);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, width, height, snapshot, 0, width);
                Path file = directory.resolve(String.format(Locale.ROOT, "%013d.png", now));
                ImageIO.write(image, "png", file.toFile());
                prune(directory);
            } catch (IOException | RuntimeException ignored) {
                // Recording is best-effort; a full disk or an unsupported file system must not break the live feed.
            }
        });
    }

    private static void prune(Path directory) throws IOException {
        List<Path> files = list(directory);
        int excess = files.size() - MAX_FRAMES_PER_CAMERA;
        for (int i = 0; i < excess; i++) Files.deleteIfExists(files.get(i));
    }

    private static List<Path> list(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public static List<Path> recordings(Minecraft mc, BlockPos cameraPos) {
        if (mc == null || cameraPos == null) return List.of();
        return new ArrayList<>(list(cameraDirectory(mc, cameraPos)));
    }

    public static int[] load(Path file, int expectedWidth, int expectedHeight) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Unsupported CCTV recording frame");
        if (image.getWidth() == expectedWidth && image.getHeight() == expectedHeight) {
            return image.getRGB(0, 0, expectedWidth, expectedHeight, null, 0, expectedWidth);
        }
        BufferedImage scaled = new BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, expectedWidth, expectedHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled.getRGB(0, 0, expectedWidth, expectedHeight, null, 0, expectedWidth);
    }

    public static String label(Path file) {
        try {
            return LABEL.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()));
        } catch (IOException ignored) {
            return file.getFileName().toString();
        }
    }
}

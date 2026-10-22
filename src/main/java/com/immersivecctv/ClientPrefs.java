package com.immersivecctv;

/** Per-player (client side) performance options for the live camera feeds. */
public final class ClientPrefs {
    private ClientPrefs() {
    }

    public static boolean liveRender = true;
    public static int quality = 0;   // 0 = 256x144, 1 = 384x216, 2 = 512x288
    public static int fpsIndex = 1;  // 0 = 5, 1 = 10, 2 = 15 renders per second (shared by all cameras)

    public static int fps() {
        return fpsIndex == 0 ? 5 : fpsIndex == 1 ? 10 : 15;
    }

    public static String qualityName() {
        return quality == 0 ? "LOW 256x144" : quality == 1 ? "MEDIUM 384x216" : "HIGH 512x288";
    }
}

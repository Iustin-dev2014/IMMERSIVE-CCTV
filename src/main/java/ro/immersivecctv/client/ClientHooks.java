package ro.immersivecctv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.List;
import ro.immersivecctv.CctvNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ro.immersivecctv.ImmersiveCCTV;

@Mod.EventBusSubscriber(modid = ImmersiveCCTV.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientHooks {
    private ClientHooks() {}

    public static void openMonitorConsole(BlockPos pos) {
        Minecraft.getInstance().setScreen(new XvrScreen(pos));
    }

    public static void openCloudRemoteLogin() {
        Minecraft.getInstance().setScreen(new CloudRemoteLoginScreen());
    }

    /** Backward-compatible call site for older item code. */
    public static void openCloudRemoteLogin(BlockPos ignoredXvrPos) {
        openCloudRemoteLogin();
    }

    public static void onCloudLoginResult(boolean success, BlockPos xvrPos, String message) {
        if (Minecraft.getInstance().screen instanceof CloudRemoteLoginScreen screen) {
            screen.showResult(success, xvrPos, message);
        } else if (!success && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("[CLOUD] " + message), false);
        }
    }

    public static void onCloudScanResult(List<CctvNetwork.ScanEntry> entries) {
        if (Minecraft.getInstance().screen instanceof CloudRemoteLoginScreen screen) {
            screen.showScanResults(entries);
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[CLOUD] Scan complete: " + entries.size() + " XVR(s) found within 100 blocks."),
                    false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) CameraViewController.tick();
    }

    /** Hide the player's hotbar, hand, crosshair and HUD overlays inside the CCTV feed. */
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof XvrScreen) event.setCanceled(true);
    }
}

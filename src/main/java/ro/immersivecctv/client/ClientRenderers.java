package ro.immersivecctv.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ro.immersivecctv.ImmersiveCCTV;

@Mod.EventBusSubscriber(modid = ImmersiveCCTV.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {
    private ClientRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ImmersiveCCTV.XVR_BE.get(), XvrCableRenderer::new);
        event.registerBlockEntityRenderer(ImmersiveCCTV.MONITOR_BE.get(), MonitorBlockEntityRenderer::new);
    }
}

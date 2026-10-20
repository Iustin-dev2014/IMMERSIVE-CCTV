package ro.immersivecctv;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** A small, fully local synthetic effects bank for power, link negotiation, relays and disk activity. */
public final class CctvSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ImmersiveCCTV.MODID);
    private static final Map<String, RegistryObject<SoundEvent>> SOUNDS = new LinkedHashMap<>();
    private static final String[] CORE = {
            "xvr_power_on", "xvr_power_off", "ac_adapter_connect", "mains_power_connect",
            "hdmi_connect", "poe_connect", "camera_restart", "camera_enable", "camera_disable",
            "monitor_power_on", "boot_ready", "boot_fail", "no_signal", "alarm_on", "alarm_off", "button_click"
    };

    static {
        for (String name : CORE) registerOne(name);
        for (int i = 1; i <= 48; i++) registerOne(String.format(java.util.Locale.ROOT, "startup_beep_%02d", i));
        for (int i = 1; i <= 48; i++) registerOne(String.format(java.util.Locale.ROOT, "camera_link_%02d", i));
        for (int i = 1; i <= 44; i++) registerOne(String.format(java.util.Locale.ROOT, "drive_seek_%02d", i));
        for (int i = 1; i <= 44; i++) registerOne(String.format(java.util.Locale.ROOT, "relay_click_%02d", i));
    }

    private CctvSounds() {}

    private static void registerOne(String name) {
        SOUNDS.put(name, SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ImmersiveCCTV.MODID, name))));
    }

    public static void register(IEventBus bus) { SOUND_EVENTS.register(bus); }
    public static SoundEvent get(String name) {
        RegistryObject<SoundEvent> sound = SOUNDS.get(name);
        if (sound == null) throw new IllegalArgumentException("Unknown CCTV sound: " + name);
        return sound.get();
    }
    public static SoundEvent randomVariant(String prefix, int count) {
        int index = RandomSource.create().nextInt(count) + 1;
        return get(prefix + String.format(java.util.Locale.ROOT, "%02d", index));
    }
}

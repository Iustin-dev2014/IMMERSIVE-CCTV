package com.immersivecctv;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(                      new ResourceLocation(ImmersiveCCTV.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {
    }

    public static void init() {
        CHANNEL.registerMessage(0, XvrActionPacket.class,
                XvrActionPacket::encode, XvrActionPacket::decode, XvrActionPacket::handle);
    }
}

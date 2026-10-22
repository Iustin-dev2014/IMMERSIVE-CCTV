package com.immersivecctv;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openScreen(BlockPos monitorPos) {
        Minecraft.getInstance().setScreen(new XvrScreen(monitorPos));
    }
}

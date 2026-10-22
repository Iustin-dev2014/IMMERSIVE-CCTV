package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: change a setting or run an action on an XVR. */
public class XvrActionPacket {
    private final BlockPos pos;
    private final int action;
    private final int arg;
    private final String text;
    private final String pin;

    public XvrActionPacket(BlockPos pos, int action, int arg, String text, String pin) {
        this.pos = pos;
        this.action = action;
        this.arg = arg;
        this.text = text;
        this.pin = pin;
    }

    public static void encode(XvrActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeInt(p.action);
        buf.writeInt(p.arg);
        buf.writeUtf(p.text, 64);
        buf.writeUtf(p.pin, 16);
    }

    public static XvrActionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int action = buf.readInt();
        int arg = buf.readInt();
        String text = buf.readUtf(64);
        String pin = buf.readUtf(16);
        return new XvrActionPacket(pos, action, arg, text, pin);
    }

    public static void handle(XvrActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            Level level = player.level();
            if (!level.hasChunkAt(p.pos)) {
                return;
            }
            if (player.distanceToSqr(p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5) > 128.0 * 128.0) {
                return;
            }
            if (level.getBlockEntity(p.pos) instanceof XvrBlockEntity xvr) {
                xvr.applyAction(player, p.action, p.arg, p.text, p.pin);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

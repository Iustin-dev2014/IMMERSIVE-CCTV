package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class CableItem extends Item {
    public enum Kind { HDMI, ETHERNET }

    private static final double MAX_ETHERNET = 64.0D;

    private final Kind kind;

    public CableItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public static void give(Player player, Item item) {
        ItemStack s = new ItemStack(item);
        if (!player.getInventory().add(s)) {
            player.drop(s, false);
        }
    }

    private static boolean isXvr(BlockState s) {
        return s.getBlock() instanceof XvrBlock;
    }

    private boolean isOther(BlockState s) {
        return kind == Kind.HDMI ? s.getBlock() instanceof MonitorBlock : s.getBlock() instanceof CameraBlock;
    }

    private String otherName() {
        return kind == Kind.HDMI ? "monitor" : "camera";
    }

    private static void say(Player p, String msg) {
        p.displayClientMessage(Component.literal(msg), true);
    }

    private static void clearLink(ItemStack stack) {
        stack.removeTagKey("LinkPos");
        stack.removeTagKey("LinkPort");
        stack.removeTagKey("LinkDim");
    }

    private static void storeLink(ItemStack stack, BlockPos pos, int port, String dim) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong("LinkPos", pos.asLong());
        tag.putInt("LinkPort", port);
        tag.putString("LinkDim", dim);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!isXvr(state) && !isOther(state)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = ctx.getItemInHand();
        String dim = level.dimension().location().toString();

        // which socket did the player click?
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 l = Ports.hitLocal(ctx.getClickLocation(), pos, facing);
        boolean rear = ctx.getClickedFace() == facing.getOpposite();
        int port = -1;
        if (rear) {
            if (isXvr(state)) {
                port = kind == Kind.HDMI ? (Ports.xvrHdmiAt(l) ? 0 : -1) : Ports.xvrLanAt(l);
            } else {
                boolean on = kind == Kind.HDMI ? Ports.monHdmiAt(l) : Ports.camLanAt(l);
                port = on ? 0 : -1;
            }
        }
        if (port < 0) {
            say(player, kind == Kind.HDMI
                    ? "Click exactly on the HDMI port on the back panel."
                    : "Click exactly on a LAN (RJ45) port on the back.");
            return InteractionResult.SUCCESS;
        }

        boolean hasLink = stack.hasTag() && stack.getTag().contains("LinkPos");
        if (!hasLink) {
            storeLink(stack, pos, port, dim);
            say(player, "Cable end 1 plugged in. Now click the " + (isXvr(state) ? otherName() : "XVR") + " port.");
            return InteractionResult.SUCCESS;
        }

        BlockPos first = BlockPos.of(stack.getTag().getLong("LinkPos"));
        int firstPort = stack.getTag().getInt("LinkPort");
        String firstDim = stack.getTag().getString("LinkDim");
        if (first.equals(pos)) {
            clearLink(stack);
            say(player, "Cable unplugged.");
            return InteractionResult.SUCCESS;
        }
        BlockState firstState = level.getBlockState(first);
        boolean firstValid = firstDim.equals(dim) && (isXvr(firstState) || isOther(firstState));
        if (!firstValid) {
            storeLink(stack, pos, port, dim);
            say(player, "The first device is gone. Cable end 1 plugged in here.");
            return InteractionResult.SUCCESS;
        }
        boolean compatible = (isXvr(firstState) && isOther(state)) || (isOther(firstState) && isXvr(state));
        if (!compatible) {
            say(player, "This cable connects an XVR with a " + otherName() + ".");
            return InteractionResult.SUCCESS;
        }

        BlockPos xvrPos = isXvr(state) ? pos : first;
        int xvrPort = isXvr(state) ? port : firstPort;
        BlockPos otherPos = isXvr(state) ? first : pos;
        if (kind == Kind.ETHERNET && xvrPos.distSqr(otherPos) > MAX_ETHERNET * MAX_ETHERNET) {
            say(player, "Cable too short (max 64 blocks).");
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(xvrPos) instanceof XvrBlockEntity xvr)) {
            return InteractionResult.SUCCESS;
        }

        boolean done = kind == Kind.HDMI
                ? connectHdmi(level, xvr, xvrPos, otherPos, player)
                : connectEthernet(level, xvr, xvrPort, otherPos, player);
        if (done) {
            clearLink(stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, xvrPos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    private boolean connectHdmi(Level level, XvrBlockEntity xvr, BlockPos xvrPos, BlockPos monPos, Player p) {
        if (!(level.getBlockEntity(monPos) instanceof MonitorBlockEntity mon)) {
            return false;
        }
        BlockPos oldMon = xvr.getMonitorPos();
        if (oldMon != null && !oldMon.equals(monPos)
                && level.getBlockEntity(oldMon) instanceof MonitorBlockEntity om) {
            om.setXvrPos(null);
        }
        BlockPos oldXvr = mon.getXvrPos();
        if (oldXvr != null && !oldXvr.equals(xvrPos)
                && level.getBlockEntity(oldXvr) instanceof XvrBlockEntity ox) {
            ox.setMonitorPos(null);
        }
        xvr.setMonitorPos(monPos);
        mon.setXvrPos(xvrPos);
        say(p, "HDMI connected: XVR -> monitor.");
        return true;
    }

    private boolean connectEthernet(Level level, XvrBlockEntity xvr, int port, BlockPos camPos, Player p) {
        int r = xvr.connectCamera(port, camPos, level.getGameTime());
        if (r == -2) {
            say(p, "This camera is already connected to the XVR.");
            return false;
        }
        if (r == -1) {
            say(p, "LAN port " + (port + 1) + " is already in use.");
            return false;
        }
        say(p, "Camera connected to LAN " + (port + 1) + " (channel " + (port + 1) + ").");
        return true;
    }
}

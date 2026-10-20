package ro.immersivecctv.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

/** Handheld diagnostic tool used to read a camera's identity and factory credentials. */
public final class CctvServiceTool extends Item {
    public CctvServiceTool(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        if (context.getLevel().getBlockEntity(pos) instanceof CameraBlockEntity camera) {
            context.getPlayer().displayClientMessage(Component.literal("[CCTV SERVICE] Camera ID: " + camera.getCameraId()), false);
            context.getPlayer().displayClientMessage(Component.literal("[CCTV SERVICE] Username: " + camera.getCredentialUsername()), false);
            context.getPlayer().displayClientMessage(Component.literal("[CCTV SERVICE] Factory password: " + (camera.hasChangedPassword() ? "changed" : "0000 (change it after first login)")), false);
            return InteractionResult.CONSUME;
        }
        if (context.getLevel().getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
            context.getPlayer().displayClientMessage(Component.literal("[XVR SERVICE] Cloud IP: " + xvr.getCloudIp()), false);
            context.getPlayer().displayClientMessage(Component.literal("[XVR SERVICE] Cloud Port: " + xvr.getCloudPort()), false);
            context.getPlayer().displayClientMessage(Component.literal(xvr.canObtainCloudEndpoint()
                    ? "[XVR SERVICE] In-game network route is available."
                    : "[XVR SERVICE] Endpoint unavailable. Check XVR power, WAN-to-router cable, router power and fibre uplink."), false);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click a CCTV camera to inspect it."));
        tooltip.add(Component.literal("Displays its unique ID and login username."));
        tooltip.add(Component.literal("Factory password: 0000 (change after first login)."));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}

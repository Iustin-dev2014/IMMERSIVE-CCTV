package ro.immersivecctv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ro.immersivecctv.LinkingManager;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;

public class CctvCableItem extends Item {
    public enum Kind { HDMI, POE }
    private final Kind kind;

    public CctvCableItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (context.getPlayer() == null) return InteractionResult.PASS;

        if (kind == Kind.HDMI && be instanceof MonitorBlockEntity) {
            if (!LinkingManager.isMonitorHdmiPort(pos, context.getClickedFace(), context.getClickLocation())) {
                context.getPlayer().displayClientMessage(Component.literal("[CCTV] Mufa HDMI este pe spatele monitorului. Clic exact pe conector, nu pe ecran."), true);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) LinkingManager.begin(context.getPlayer(), LinkingManager.Endpoint.MONITOR, pos, kind);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == Kind.POE && be instanceof CameraBlockEntity) {
            if (!LinkingManager.isCameraPoePort(level, pos, context.getClickedFace(), context.getClickLocation())) {
                context.getPlayer().displayClientMessage(Component.literal("[CCTV] Mufa PoE este pe spatele camerei. Clic exact pe conector."), true);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) LinkingManager.begin(context.getPlayer(), LinkingManager.Endpoint.CAMERA, pos, kind);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (be instanceof XvrBlockEntity) {
            if (!level.isClientSide) {
                boolean handled = LinkingManager.completePending(context.getPlayer(), level, pos,
                        context.getClickedFace(), context.getClickLocation(), kind);
                if (!handled) context.getPlayer().displayClientMessage(Component.literal("[CCTV] Port XVR invalid."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}

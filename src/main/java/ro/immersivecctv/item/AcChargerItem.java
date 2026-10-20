package ro.immersivecctv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.sounds.SoundSource;
import ro.immersivecctv.CctvSounds;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import ro.immersivecctv.blockentity.RouterBlockEntity;

/** Installable 12 V DC power adapter for the recorder's rear barrel socket. */
public class AcChargerItem extends Item {
    public AcChargerItem(Properties properties) { super(properties); }

    private InteractionResult wrongPort(net.minecraft.world.entity.player.Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
        return InteractionResult.FAIL;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Vec3 hit = context.getClickLocation();
        double x = hit.x - pos.getX(), y = hit.y - pos.getY(), z = hit.z - pos.getZ();
        if (level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
            boolean correctPort = context.getClickedFace() == Direction.SOUTH && z >= 0.88
                    && x >= 0.76 && x <= 0.98 && y >= 0.10 && y <= 0.35;
            if (!correctPort) return wrongPort(player, "[XVR] Conecteaza adaptorul la mufa DC rotunda din spatele recorderului.");
            if (!level.isClientSide) {
                if (xvr.has12VAdapter()) player.displayClientMessage(Component.literal("[XVR] Adaptorul 12V este deja conectat."), true);
                else {
                    xvr.set12VAdapterInstalled(true);
                    level.playSound(null, pos, CctvSounds.get("ac_adapter_connect"), SoundSource.BLOCKS, 0.8F, 1.0F);
                    if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
                    player.displayClientMessage(Component.literal("[XVR] AC Charger 12V conectat. Mai trebuie cablu Power Cable catre o priza."), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            boolean correctPort = context.getClickedFace() == Direction.SOUTH && z >= 0.86
                    && x >= 0.72 && x <= 0.94 && y >= 0.16 && y <= 0.56;
            if (!correctPort) return wrongPort(player, "[ROUTER] Conecteaza adaptorul 12V la mufa DC de pe panoul din spate.");
            if (!level.isClientSide) {
                if (router.has12VAdapter()) player.displayClientMessage(Component.literal("[ROUTER] AC Charger 12V is already connected."), true);
                else {
                    router.set12VAdapterInstalled(true);
                    level.playSound(null, pos, CctvSounds.get("ac_adapter_connect"), SoundSource.BLOCKS, 0.65F, 1.15F);
                    if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
                    player.displayClientMessage(Component.literal("[ROUTER] 12V adapter connected. Use Power Cable to connect it to a wall outlet."), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}

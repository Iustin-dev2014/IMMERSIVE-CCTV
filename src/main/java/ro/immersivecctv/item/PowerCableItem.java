package ro.immersivecctv.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.sounds.SoundSource;
import ro.immersivecctv.CctvSounds;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.block.PowerOutletBlock;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import ro.immersivecctv.blockentity.RouterBlockEntity;

/** Two-end mains lead: first click an outlet face, then the actual power jack on a device. */
public class PowerCableItem extends Item {
    private static final Map<UUID, BlockPos> PENDING_OUTLET = new ConcurrentHashMap<>();
    public PowerCableItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        if (level.getBlockState(pos).is(ImmersiveCCTV.POWER_OUTLET.get())) {
            Direction facing = level.getBlockState(pos).getValue(PowerOutletBlock.FACING);
            if (context.getClickedFace() != facing || !insideSocket(pos, hit, facing)) {
                player.displayClientMessage(Component.literal("[POWER] Apasa pe fata prizei, direct pe una dintre gaurile de contact."), true);
                return InteractionResult.FAIL;
            }
            PENDING_OUTLET.put(player.getUUID(), pos.immutable());
            player.displayClientMessage(Component.literal("[POWER] Priza selectata. Acum apasa mufa DC de pe monitor, XVR sau router."), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos outlet = PENDING_OUTLET.get(player.getUUID());
        if (outlet == null) {
            player.displayClientMessage(Component.literal("[POWER] Mai intai selecteaza fata unei prize cu Power Cable in mana."), true);
            return InteractionResult.FAIL;
        }
        if (!level.getBlockState(outlet).is(ImmersiveCCTV.POWER_OUTLET.get())) {
            PENDING_OUTLET.remove(player.getUUID());
            player.displayClientMessage(Component.literal("[POWER] Priza selectata nu mai exista."), true);
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
            boolean port = context.getClickedFace() == Direction.SOUTH && hit.z - pos.getZ() >= 0.88
                    && hit.x - pos.getX() >= 0.76 && hit.x - pos.getX() <= 0.98
                    && hit.y - pos.getY() >= 0.10 && hit.y - pos.getY() <= 0.35;
            if (!port) return wrongPort(player, "XVR: mufa de alimentare DC de pe panoul din spate.");
            if (!xvr.has12VAdapter()) {
                player.displayClientMessage(Component.literal("[POWER] XVR-ul cere mai intai un AC Charger 12V conectat la mufa DC."), true);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                xvr.setPowerOutletPos(outlet);
                level.playSound(null, pos, CctvSounds.get("mains_power_connect"), SoundSource.BLOCKS, 0.8F, 1.0F);
                PENDING_OUTLET.remove(player.getUUID());
                player.displayClientMessage(Component.literal("[POWER] XVR conectat la reteaua electrica. Poti apasa acum butonul POWER."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            boolean port = context.getClickedFace() == Direction.SOUTH && hit.z - pos.getZ() >= 0.86
                    && hit.x - pos.getX() >= 0.72 && hit.x - pos.getX() <= 0.94
                    && hit.y - pos.getY() >= 0.16 && hit.y - pos.getY() <= 0.56;
            if (!port) return wrongPort(player, "Router: the 12V DC jack on its rear panel.");
            if (!router.has12VAdapter()) {
                player.displayClientMessage(Component.literal("[POWER] The router needs its AC Charger 12V adapter before connecting the power lead."), true);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                router.setPowerOutletPos(outlet);
                level.playSound(null, pos, CctvSounds.get("mains_power_connect"), SoundSource.BLOCKS, 0.7F, 1.18F);
                PENDING_OUTLET.remove(player.getUUID());
                player.displayClientMessage(Component.literal("[POWER] Router connected to mains power. You can now press its front POWER button."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            boolean port = context.getClickedFace() == Direction.SOUTH && hit.z - pos.getZ() >= 0.88
                    && hit.x - pos.getX() >= 0.30 && hit.x - pos.getX() <= 0.70
                    && hit.y - pos.getY() >= 0.25 && hit.y - pos.getY() <= 0.50;
            if (!port) return wrongPort(player, "Monitor: mufa AC de pe spatele monitorului.");
            if (!level.isClientSide) {
                monitor.setPowerOutletPos(outlet);
                level.playSound(null, pos, CctvSounds.get("monitor_power_on"), SoundSource.BLOCKS, 0.72F, 1.0F);
                PENDING_OUTLET.remove(player.getUUID());
                player.displayClientMessage(Component.literal("[POWER] Monitor conectat la priza. HDMI-ul ramane o conexiune separata."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return wrongPort(player, "dispozitiv CCTV compatibil (monitor, XVR sau router).");
    }

    private InteractionResult wrongPort(Player player, String label) {
        player.displayClientMessage(Component.literal("[POWER] Conecteaza cablul la " + label), true);
        return InteractionResult.FAIL;
    }

    private boolean insideSocket(BlockPos pos, Vec3 hit, Direction facing) {
        double x = hit.x - pos.getX(), y = hit.y - pos.getY(), z = hit.z - pos.getZ();
        if (y < 0.20 || y > 0.80) return false;
        return switch (facing) {
            case NORTH, SOUTH -> x >= 0.20 && x <= 0.80;
            case EAST, WEST -> z >= 0.20 && z <= 0.80;
            default -> false;
        };
    }
}

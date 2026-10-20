package ro.immersivecctv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import ro.immersivecctv.ImmersiveCCTV;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ro.immersivecctv.client.ClientHooks;

/** Handheld cloud viewer. It authenticates to a powered XVR on the simulated in-world network. */
public final class CloudAccessRemoteItem extends Item {
    public CloudAccessRemoteItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !level.getBlockState(pos).is(ImmersiveCCTV.XVR.get())) return InteractionResult.PASS;
        if (level.getBlockEntity(pos) instanceof XvrBlockEntity xvr) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openCloudRemoteLogin(pos));
            } else {
                player.displayClientMessage(Component.literal(xvr.canObtainCloudEndpoint()
                        ? "[CLOUD] XVR endpoint available. Enter the XVR credentials in the remote terminal."
                        : "[CLOUD] Host unreachable. Check the XVR, router WAN, router power and fibre uplink."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("[CLOUD] Right-click an XVR to open the Cloud Access login terminal."), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}

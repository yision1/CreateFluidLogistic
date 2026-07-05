package com.yision.fluidlogistics.handler;

import com.simibubi.create.AllBlocks;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.network.ClipboardSetAddressPacket;
import com.yision.fluidlogistics.util.PackagerTargetHelper;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClipboardPasteHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack heldItem = event.getItemStack();
        if (!AllBlocks.CLIPBOARD.isIn(heldItem)) {
            return;
        }
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!PackagerTargetHelper.isClipboardAddressBlock(level.getBlockState(pos))) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!level.isClientSide()) {
            return;
        }

        event.getEntity().swing(event.getHand());
        FluidLogisticsPackets.getChannel().sendToServer(new ClipboardSetAddressPacket(pos));
    }
}

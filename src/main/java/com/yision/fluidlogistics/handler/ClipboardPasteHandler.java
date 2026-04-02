package com.yision.fluidlogistics.handler;

import com.simibubi.create.AllBlocks;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.network.ClipboardSetAddressPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
public class ClipboardPasteHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();
        if (!AllBlocks.CLIPBOARD.isIn(heldItem)) {
            return;
        }
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!AllBlocks.PACKAGER.has(level.getBlockState(pos))
            && !AllBlocks.REPACKAGER.has(level.getBlockState(pos))
            && !com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(level.getBlockState(pos))) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.getEntity().swing(event.getHand());
        ClipboardSetAddressPacket.send(pos);
    }
}

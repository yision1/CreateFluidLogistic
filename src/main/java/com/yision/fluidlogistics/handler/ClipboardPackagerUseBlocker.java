package com.yision.fluidlogistics.handler;

import com.simibubi.create.AllBlocks;
import com.yision.fluidlogistics.FluidLogistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = FluidLogistics.MODID)
public class ClipboardPackagerUseBlocker {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !shouldBlockClipboardUse(event.getItemStack(), event.getEntity())) {
            return;
        }

        if (!isBlockedPackager(event.getLevel(), event.getPos())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !shouldBlockClipboardUse(event.getItemStack(), event.getEntity())) {
            return;
        }

        HitResult hitResult = event.getEntity().pick(5.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        if (!isBlockedPackager(event.getLevel(), blockHitResult.getBlockPos())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static boolean shouldBlockClipboardUse(ItemStack stack, Player player) {
        return AllBlocks.CLIPBOARD.isIn(stack) && !player.isShiftKeyDown();
    }

    private static boolean isBlockedPackager(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return AllBlocks.PACKAGER.has(state)
            || AllBlocks.REPACKAGER.has(state)
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
    }
}

package com.yision.fluidlogistics.handler;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.registry.AllBlocks;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FluidLogistics.MODID)
public class MechanicalFluidGunTargetUseBlocker {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        if (player.isSpectator()
            || player.isShiftKeyDown()
            || !AllBlocks.MECHANICAL_FLUID_GUN.isIn(player.getMainHandItem())) {
            return;
        }

        if (!MechanicalFluidGunBlock.isTargetTagged(level, event.getPos())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

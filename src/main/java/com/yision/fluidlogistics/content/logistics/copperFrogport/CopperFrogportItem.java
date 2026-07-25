package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.logistics.packagePort.PackagePortItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.yision.fluidlogistics.FluidLogistics;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@EventBusSubscriber(modid = FluidLogistics.MODID)
public class CopperFrogportItem extends PackagePortItem {

    public CopperFrogportItem(Block block, Properties properties) {
        super(block, properties);
    }

    @SubscribeEvent
    public static void handleRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof CopperFrogportItem)) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof ChainConveyorBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (state.getBlock() instanceof PackagerBlock) {
            event.setUseBlock(Event.Result.DENY);
        }
    }
}

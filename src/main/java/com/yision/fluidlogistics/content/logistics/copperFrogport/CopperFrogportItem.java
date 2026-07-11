package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.yision.fluidlogistics.FluidLogistics;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = FluidLogistics.MODID)
public class CopperFrogportItem extends BlockItem {

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
            event.setUseBlock(TriState.FALSE);
        }
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
                                                   BlockState state) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            CatnipServices.NETWORK.sendToClient(
                serverPlayer,
                new CopperFrogportPlacementRequestPacket(pos)
            );
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}

package com.yision.fluidlogistics.network;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import net.createmod.catnip.data.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent.Context;

public class FactoryPanelSetFluidFilterPacket extends SimplePacketBase {

    private final FactoryPanelPosition panelPosition;
    private final InteractionHand hand;

    public FactoryPanelSetFluidFilterPacket(FactoryPanelPosition panelPosition, InteractionHand hand) {
        this.panelPosition = panelPosition;
        this.hand = hand;
    }

    public FactoryPanelSetFluidFilterPacket(FriendlyByteBuf buffer) {
        this.panelPosition = FactoryPanelPosition.receive(buffer);
        this.hand = buffer.readEnum(InteractionHand.class);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        panelPosition.send(buffer);
        buffer.writeEnum(hand);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            if (!com.yision.fluidlogistics.config.Config.isAdvancedLogisticsNetworkEnabled()) {
                return;
            }

            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), panelPosition);
            if (behaviour == null) {
                return;
            }

            ItemStack heldItem = player.getItemInHand(hand);
            if (heldItem.isEmpty() || !GenericItemEmptying.canItemBeEmptied(player.level(), heldItem)) {
                return;
            }

            Pair<FluidStack, ItemStack> emptyResult = GenericItemEmptying.emptyItem(player.level(), heldItem, true);
            FluidStack fluidStack = emptyResult.getFirst();
            if (fluidStack.isEmpty()) {
                return;
            }

            ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            FluidStack template = fluidStack.copy();
            template.setAmount(1);
            CompressedTankItem.setFluidVirtual(virtualTank, template);

            if (!behaviour.setFilter(virtualTank)) {
                return;
            }

            if (!player.isCreative()) {
                heldItem.shrink(1);
                if (!emptyResult.getSecond().isEmpty()) {
                    player.getInventory().placeItemBackInInventory(emptyResult.getSecond());
                }
            }

            player.level().playSound(null, behaviour.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS,
                0.25f, 0.1f);
        });
        return true;
    }
}

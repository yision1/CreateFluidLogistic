package com.yision.fluidlogistics.network.factoryPanel;

import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.simibubi.create.Create;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public record FactoryPanelSetFluidFilterPacket(
        FactoryPanelPosition panelPosition,
        InteractionHand hand
) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, FactoryPanelSetFluidFilterPacket> STREAM_CODEC = 
            StreamCodec.of(
                    FactoryPanelSetFluidFilterPacket::encode,
                    FactoryPanelSetFluidFilterPacket::decode
            );

    private static void encode(RegistryFriendlyByteBuf buf, FactoryPanelSetFluidFilterPacket packet) {
        FactoryPanelPosition.STREAM_CODEC.encode(buf, packet.panelPosition);
        buf.writeEnum(packet.hand);
    }

    private static FactoryPanelSetFluidFilterPacket decode(RegistryFriendlyByteBuf buf) {
        FactoryPanelPosition panelPosition = FactoryPanelPosition.STREAM_CODEC.decode(buf);
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        return new FactoryPanelSetFluidFilterPacket(panelPosition, hand);
    }

    @Override
    public void handle(ServerPlayer player) {

        FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), panelPosition);
        if (behaviour == null) {
            return;
        }
        
        if (!Create.LOGISTICS.mayInteract(behaviour.network, player)) {
            return;
        }
        
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.isEmpty()) {
            return;
        }
        
        if (!GenericItemEmptying.canItemBeEmptied(player.level(), heldItem)) {
            return;
        }
        
        Pair<FluidStack, ItemStack> emptyResult = GenericItemEmptying.emptyItem(
                player.level(), heldItem, true);
        FluidStack fluidStack = emptyResult.getFirst();
        
        if (fluidStack.isEmpty()) {
            return;
        }
        
        ItemStack fluidTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(fluidTank, fluidStack.copyWithAmount(1));
        
        if (behaviour.setFilter(fluidTank)) {
            if (!player.isCreative()) {
                heldItem.shrink(1);
                if (!emptyResult.getSecond().isEmpty()) {
                    player.getInventory().placeItemBackInInventory(emptyResult.getSecond());
                }
            }
            
            player.level().playSound(null, behaviour.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, 
                    SoundSource.BLOCKS, 0.25f, 0.1f);
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.FACTORY_PANEL_SET_FLUID_FILTER;
    }
}

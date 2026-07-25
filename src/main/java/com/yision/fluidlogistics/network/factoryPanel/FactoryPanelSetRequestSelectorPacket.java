package com.yision.fluidlogistics.network.factoryPanel;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public class FactoryPanelSetRequestSelectorPacket extends SimplePacketBase {
    private final FactoryPanelPosition panelPosition;
    private final InteractionHand hand;

    public FactoryPanelSetRequestSelectorPacket(FactoryPanelPosition panelPosition, InteractionHand hand) {
        this.panelPosition = panelPosition;
        this.hand = hand;
    }

    public FactoryPanelSetRequestSelectorPacket(FriendlyByteBuf buffer) {
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
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), panelPosition);
            if (behaviour == null || !Create.LOGISTICS.mayInteract(behaviour.network, player)) {
                return;
            }
            ItemStack key = PackageResources.resolveRequestKey(player.getItemInHand(hand)).orElse(ItemStack.EMPTY);
            if (key.isEmpty() || !behaviour.setFilter(key)) {
                return;
            }
            player.level().playSound(null, behaviour.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, 0.25f, 0.1f);
        });
        return true;
    }
}

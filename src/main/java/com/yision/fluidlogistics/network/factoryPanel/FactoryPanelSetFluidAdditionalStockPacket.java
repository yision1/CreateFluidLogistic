package com.yision.fluidlogistics.network.factoryPanel;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidAdditionalStock;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class FactoryPanelSetFluidAdditionalStockPacket extends SimplePacketBase {

    private final FactoryPanelPosition panelPosition;
    private final int additionalStock;

    public FactoryPanelSetFluidAdditionalStockPacket(FactoryPanelPosition panelPosition, int additionalStock) {
        this.panelPosition = panelPosition;
        this.additionalStock = additionalStock;
    }

    public FactoryPanelSetFluidAdditionalStockPacket(FriendlyByteBuf buffer) {
        this.panelPosition = FactoryPanelPosition.receive(buffer);
        this.additionalStock = buffer.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        panelPosition.send(buffer);
        buffer.writeVarInt(additionalStock);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            FluidGaugeHelper.applyPanelSetting(player, panelPosition, IFluidAdditionalStock.class,
                additionalStockData -> additionalStockData.fluidlogistics$setAdditionalStock(additionalStock));
        });
        return true;
    }
}

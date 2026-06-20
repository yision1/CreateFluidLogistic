package com.yision.fluidlogistics.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidRestockThreshold;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class FactoryPanelSetFluidRestockThresholdPacket extends SimplePacketBase {

    private final FactoryPanelPosition panelPosition;
    private final int threshold;

    public FactoryPanelSetFluidRestockThresholdPacket(FactoryPanelPosition panelPosition, int threshold) {
        this.panelPosition = panelPosition;
        this.threshold = threshold;
    }

    public FactoryPanelSetFluidRestockThresholdPacket(FriendlyByteBuf buffer) {
        this.panelPosition = FactoryPanelPosition.receive(buffer);
        this.threshold = buffer.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        panelPosition.send(buffer);
        buffer.writeVarInt(threshold);
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

            FluidGaugeHelper.applyPanelSetting(player, panelPosition, IFluidRestockThreshold.class,
                thresholdData -> thresholdData.fluidlogistics$setRestockThreshold(threshold));
        });
        return true;
    }
}

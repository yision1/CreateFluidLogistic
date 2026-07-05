package com.yision.fluidlogistics.network.factoryPanel;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidPromiseLimit;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class FactoryPanelSetFluidPromiseLimitPacket extends SimplePacketBase {

    private final FactoryPanelPosition panelPosition;
    private final int limit;

    public FactoryPanelSetFluidPromiseLimitPacket(FactoryPanelPosition panelPosition, int limit) {
        this.panelPosition = panelPosition;
        this.limit = limit;
    }

    public FactoryPanelSetFluidPromiseLimitPacket(FriendlyByteBuf buffer) {
        this.panelPosition = FactoryPanelPosition.receive(buffer);
        this.limit = buffer.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        panelPosition.send(buffer);
        buffer.writeVarInt(limit);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            FluidGaugeHelper.applyPanelSetting(player, panelPosition, IFluidPromiseLimit.class,
                promiseLimitData -> promiseLimitData.fluidlogistics$setPromiseLimit(limit));
        });
        return true;
    }
}

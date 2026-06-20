package com.yision.fluidlogistics.compat.emi;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
@SuppressWarnings("unused")
public class FluidLogisticsEMI implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(FactoryPanelSetItemScreen.class, FactoryPanelSetItemEmiDragDropHandler.INSTANCE);
        registry.addDragDropHandler(RedstoneRequesterScreen.class, RedstoneRequesterEmiDragDropHandler.INSTANCE);

        registry.addStackProvider(FactoryPanelSetItemScreen.class, EmiVirtualFluidStackProvider.FactoryPanel.INSTANCE);
        registry.addStackProvider(RedstoneRequesterScreen.class, EmiVirtualFluidStackProvider.RedstoneRequester.INSTANCE);
        registry.addStackProvider(StockKeeperRequestScreen.class, EmiVirtualFluidStackProvider.StockKeeper.INSTANCE);
    }
}

package com.yision.fluidlogistics.compat.jei;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.client.JeiRuntimeHolder;
import com.yision.fluidlogistics.portableticker.PortableStockTickerScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
@SuppressWarnings("unused")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidLogisticsJEI implements IModPlugin {

    private static final ResourceLocation ID = FluidLogistics.asResource("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new PortableStockTickerTransferHandler());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FactoryPanelSetItemScreen.class, FactoryPanelSetItemFluidGhostHandler.INSTANCE);
        registration.addGhostIngredientHandler(RedstoneRequesterScreen.class, RedstoneRequesterFluidGhostHandler.INSTANCE);
        registration.addGuiContainerHandler(StockKeeperRequestScreen.class, new StockKeeperRequestFluidGuiHandler());
        registration.addGuiContainerHandler(PortableStockTickerScreen.class, new PortableStockTickerGuiHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiRuntimeHolder.setRuntime(runtime);
    }
}

package com.yision.fluidlogistics.compat.jei;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.handpointer.filter.HandPointerFilterScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
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
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!CompatMods.emiLoaded()) {
            registration.addGhostIngredientHandler(FactoryPanelSetItemScreen.class, FactoryPanelSetItemFluidGhostHandler.INSTANCE);
            registration.addGhostIngredientHandler(RedstoneRequesterScreen.class, RedstoneRequesterFluidGhostHandler.INSTANCE);
        }
        registration.addGhostIngredientHandler(HandPointerFilterScreen.class, HandPointerFilterGhostHandler.INSTANCE);
        registration.addGuiContainerHandler(StockKeeperRequestScreen.class, new StockKeeperRequestFluidGuiHandler());
    }
}

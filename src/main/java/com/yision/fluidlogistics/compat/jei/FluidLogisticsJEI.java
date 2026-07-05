package com.yision.fluidlogistics.compat.jei;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.filter.AbstractFilterScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterScreen;
import com.yision.fluidlogistics.mixin.accessor.RedstoneRequesterScreenAccessor;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@JeiPlugin
@SuppressWarnings("unused")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidLogisticsJEI implements IModPlugin {

    private static final ResourceLocation ID = FluidLogistics.asResource("jei_plugin");

    private static final FluidGhostIngredientHandler<AbstractFilterScreen<?>> FILTER_FLUID_HANDLER =
        new FluidGhostIngredientHandler<>();
    private static final FluidGhostIngredientHandler<FactoryPanelSetItemScreen> FACTORY_PANEL_FLUID_HANDLER =
        new FluidGhostIngredientHandler<>();
    private static final FluidGhostIngredientHandler<RedstoneRequesterScreen> REDSTONE_REQUESTER_FLUID_HANDLER =
        new FluidGhostIngredientHandler<>((gui, slotIndex) ->
            ((RedstoneRequesterScreenAccessor) gui).getAmounts()
                .set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT));

    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Nullable
    static IJeiRuntime getRuntime() {
        return runtime;
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeManagerPlugin(new VirtualFluidTankRecipeLookupPlugin(
            registration.getJeiHelpers(), FluidLogisticsJEI::getRuntime));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!CompatMods.emiLoaded()) {
            registration.addGhostIngredientHandler((Class) AbstractFilterScreen.class, FILTER_FLUID_HANDLER);
            registration.addGhostIngredientHandler(FactoryPanelSetItemScreen.class, FACTORY_PANEL_FLUID_HANDLER);
            registration.addGhostIngredientHandler(RedstoneRequesterScreen.class, REDSTONE_REQUESTER_FLUID_HANDLER);
            registration.addGhostIngredientHandler(HandPointerFilterScreen.class, HandPointerFilterGhostHandler.INSTANCE);
        }
        registration.addGuiContainerHandler(StockKeeperRequestScreen.class,
            new StockKeeperRequestFluidGuiHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}

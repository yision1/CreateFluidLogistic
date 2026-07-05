package com.yision.fluidlogistics.compat.emi;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.content.logistics.filter.FilterScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterScreen;
import com.yision.fluidlogistics.mixin.accessor.RedstoneRequesterScreenAccessor;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
@SuppressWarnings("unused")
public class FluidLogisticsEMI implements EmiPlugin {

    private static final EmiDragDropHandler<FilterScreen> FILTER_DRAG_DROP =
        new GhostSlotEmiDragDropHandler<>(GhostSlotEmiDragDropHandler.AcceptMode.FLUID_ONLY);
    private static final EmiDragDropHandler<AttributeFilterScreen> ATTRIBUTE_FILTER_DRAG_DROP =
        new GhostSlotEmiDragDropHandler<>(GhostSlotEmiDragDropHandler.AcceptMode.FLUID_ONLY);
    private static final EmiDragDropHandler<FactoryPanelSetItemScreen> FACTORY_PANEL_DRAG_DROP =
        new GhostSlotEmiDragDropHandler<>(GhostSlotEmiDragDropHandler.AcceptMode.ITEM_OR_FLUID);
    private static final EmiDragDropHandler<RedstoneRequesterScreen> REDSTONE_REQUESTER_DRAG_DROP =
        new GhostSlotEmiDragDropHandler<>(GhostSlotEmiDragDropHandler.AcceptMode.ITEM_OR_FLUID,
            (gui, slotIndex, fromFluid) -> {
                if (!fromFluid) {
                    return;
                }
                ((RedstoneRequesterScreenAccessor) gui).getAmounts()
                    .set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);
            });
    private static final EmiDragDropHandler<HandPointerFilterScreen> HAND_POINTER_DRAG_DROP =
        new GhostSlotEmiDragDropHandler<>(GhostSlotEmiDragDropHandler.AcceptMode.ITEM_OR_FLUID);

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(FilterScreen.class, FILTER_DRAG_DROP);
        registry.addDragDropHandler(AttributeFilterScreen.class, ATTRIBUTE_FILTER_DRAG_DROP);
        registry.addDragDropHandler(FactoryPanelSetItemScreen.class, FACTORY_PANEL_DRAG_DROP);
        registry.addDragDropHandler(RedstoneRequesterScreen.class, REDSTONE_REQUESTER_DRAG_DROP);
        registry.addDragDropHandler(HandPointerFilterScreen.class, HAND_POINTER_DRAG_DROP);

        registry.addStackProvider(FilterScreen.class, EmiVirtualFluidStackProvider.Filter.INSTANCE);
        registry.addStackProvider(AttributeFilterScreen.class, EmiVirtualFluidStackProvider.AttributeFilter.INSTANCE);
        registry.addStackProvider(FactoryPanelSetItemScreen.class, EmiVirtualFluidStackProvider.FactoryPanel.INSTANCE);
        registry.addStackProvider(RedstoneRequesterScreen.class, EmiVirtualFluidStackProvider.RedstoneRequester.INSTANCE);
        registry.addStackProvider(HandPointerFilterScreen.class, EmiVirtualFluidStackProvider.HandPointerFilter.INSTANCE);
        registry.addStackProvider(StockKeeperRequestScreen.class, EmiVirtualFluidStackProvider.StockKeeper.INSTANCE);
    }
}

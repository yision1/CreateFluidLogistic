package com.yision.fluidlogistics.api;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;

public interface IFluidPackager {

    InventorySummary getAvailableItems();

    boolean isTargetingSameInventory(@Nullable IdentifiedInventory inventory);

    void triggerStockCheck();

    Pair<PackagerBlockEntity, PackagingRequest> processFluidRequest(ItemStack stack, int amount, String address,
            int linkIndex, MutableBoolean finalLink, int orderId, @Nullable PackageOrderWithCrafts context,
            @Nullable IdentifiedInventory ignoredHandler);

    void attemptToSendFluidRequest(java.util.List<PackagingRequest> queuedRequests);

    void flashFluidLink();

    boolean isFluidPackagerTooBusy(com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType type);

    @Nullable
    IdentifiedInventory getIdentifiedInventory();
}

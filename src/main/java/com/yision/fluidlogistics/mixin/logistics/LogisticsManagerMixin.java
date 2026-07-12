package com.yision.fluidlogistics.mixin.logistics;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(LogisticsManager.class)
public abstract class LogisticsManagerMixin {

    @WrapOperation(
        method = "findPackagersForRequest",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour;"
                + "processRequest(Lnet/minecraft/world/item/ItemStack;ILjava/lang/String;IL"
                + "org/apache/commons/lang3/mutable/MutableBoolean;IL"
                + "com/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;"
                + "Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;)"
                + "Lnet/createmod/catnip/data/Pair;"),
        remap = false)
    private static Pair<PackagerBlockEntity, PackagingRequest> fluidlogistics$routeFluidRequest(
            LogisticallyLinkedBehaviour link, ItemStack requestedItem, int remainingCount, String address,
            int linkIndex, MutableBoolean isFinalLink, int orderId, PackageOrderWithCrafts context,
            IdentifiedInventory ignoredHandler,
            Operation<Pair<PackagerBlockEntity, PackagingRequest>> original) {

        if (fluidlogistics$isFluidRequest(requestedItem)) {
            IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
            if (fluidPackager == null || fluidPackager.isTargetingSameInventory(ignoredHandler)) {
                return null;
            }
            return fluidPackager.processFluidRequest(requestedItem, remainingCount, address,
                linkIndex, isFinalLink, orderId, context, ignoredHandler);
        }

        if (link.blockEntity instanceof PackagerLinkBlockEntity plbe
                && plbe.getPackager() instanceof IFluidPackager) {
            return null;
        }

        return original.call(link, requestedItem, remainingCount, address,
            linkIndex, isFinalLink, orderId, context, ignoredHandler);
    }

    @Inject(method = "getInventoryIdentifierFromLink", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fluidlogistics$identifyFluidPackagerInventory(LogisticallyLinkedBehaviour link,
            CallbackInfoReturnable<InventoryIdentifier> cir) {
        IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
        if (fluidPackager instanceof BlockEntity blockEntity) {
            cir.setReturnValue(new InventoryIdentifier.Single(blockEntity.getBlockPos()));
        }
    }

    @Unique
    private static boolean fluidlogistics$isFluidRequest(ItemStack stack) {
        return CompressedTankItem.isFluidStack(stack);
    }

    @Unique
    @Nullable
    private static IFluidPackager fluidlogistics$getFluidPackagerFromLink(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
            return null;
        }

        if (link.redstonePower == 15) {
            return null;
        }

        BlockPos source = plbe.getBlockPos().relative(
            PackagerLinkBlock.getConnectedDirection(plbe.getBlockState()).getOpposite());
        BlockEntity blockEntity = plbe.getLevel().getBlockEntity(source);

        if (blockEntity instanceof IFluidPackager fluidPackager) {
            return fluidPackager;
        }
        return null;
    }
}

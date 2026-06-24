package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.yision.fluidlogistics.goggle.PackagerGoggleInfo;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerBlockEntityMixin implements IPackagerOverrideData, IHaveGoggleInformation {

    @Unique
    private boolean fluidlogistics$manualOverrideLocked;
    @Unique
    private String fluidlogistics$clipboardAddress = "";
    @Unique
    private int fluidlogistics$queuedPackageCount;

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$rejectCompressedTankPackages(ItemStack box, boolean simulate,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (fluidlogistics$containsCompressedTank(box)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void fluidlogistics$writeOverrideData(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        compound.putBoolean("FluidLogisticsManualOverrideLocked", fluidlogistics$manualOverrideLocked);
        compound.putString("FluidLogisticsClipboardAddress", fluidlogistics$clipboardAddress);
        compound.putInt("FluidLogisticsQueuedPackageCount",
            fluidlogistics$countQueuedPackages((PackagerBlockEntity) (Object) this));
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void fluidlogistics$readOverrideData(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        fluidlogistics$manualOverrideLocked = compound.getBoolean("FluidLogisticsManualOverrideLocked");
        fluidlogistics$clipboardAddress = compound.getString("FluidLogisticsClipboardAddress");
        fluidlogistics$queuedPackageCount = compound.getInt("FluidLogisticsQueuedPackageCount");
    }

    @Inject(method = "updateSignAddress", at = @At("RETURN"))
    private void fluidlogistics$applyClipboardAddressFallback(CallbackInfo ci) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        Level level = packager.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        if (!packager.signBasedAddress.isBlank() || fluidlogistics$clipboardAddress.isBlank()) {
            return;
        }

        packager.signBasedAddress = fluidlogistics$clipboardAddress;
    }

    @Inject(
        method = "getAvailableItems(Z)Lcom/simibubi/create/content/logistics/packager/InventorySummary;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/InventorySummary;add(Lnet/minecraft/world/item/ItemStack;)V"
        ),
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void fluidlogistics$ensureCurrentSlotIdentityBeforeSummarizing(boolean scanInputSlots,
        CallbackInfoReturnable<InventorySummary> cir, InventorySummary availableItems, IItemHandler targetInv,
        @Local(ordinal = 0) int slot) {
        fluidlogistics$ensureIdentityInSlot(targetInv, slot);
    }

    @Unique
    private static void fluidlogistics$ensureIdentityInSlot(IItemHandler targetInv, int slot) {
        ItemStack stackInSlot = targetInv.getStackInSlot(slot);
        if (!(stackInSlot.getItem() instanceof CompressedTankItem) || CompressedTankItem.isVirtual(stackInSlot)) {
            return;
        }

        if (targetInv instanceof IItemHandlerModifiable modifiable) {
            ItemStack updated = stackInSlot.copy();
            CompressedTankItem.ensureIdentity(updated);
            if (!ItemStack.isSameItemSameTags(updated, stackInSlot)) {
                modifiable.setStackInSlot(slot, updated);
            }
            return;
        }

        CompressedTankItem.ensureIdentity(stackInSlot);
    }

    @Unique
    private static boolean fluidlogistics$containsCompressedTank(ItemStack box) {
        if (box.isEmpty() || !PackageItem.isPackage(box)) {
            return false;
        }

        ItemStackHandler contents = PackageItem.getContents(box);
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            if (contents.getStackInSlot(slot).getItem() instanceof CompressedTankItem) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        BlockState state = packager.getBlockState();
        boolean isRepackager = packager instanceof RepackagerBlockEntity;
        boolean isLinkedToNetwork = state.hasProperty(PackagerBlock.LINKED) && state.getValue(PackagerBlock.LINKED);

        if (isRepackager) {
            int cachedPackageCount = fluidlogistics$countCachedPackages(packager);
            if (!fluidlogistics$manualOverrideLocked && cachedPackageCount <= 0) {
                return false;
            }
            PackagerGoggleInfo.addToTooltip(tooltip, "", fluidlogistics$manualOverrideLocked, true,
                false, cachedPackageCount);
            return true;
        }

        String address = fluidlogistics$resolveAddress(packager);
        PackagerGoggleInfo.addToTooltip(tooltip, address, fluidlogistics$manualOverrideLocked, isRepackager,
            isLinkedToNetwork);
        return true;
    }

    @Unique
    private int fluidlogistics$countCachedPackages(PackagerBlockEntity packager) {
        Level level = packager.getLevel();
        if (level != null && level.isClientSide) {
            return fluidlogistics$queuedPackageCount;
        }
        return fluidlogistics$countQueuedPackages(packager);
    }

    @Unique
    private static int fluidlogistics$countQueuedPackages(PackagerBlockEntity packager) {
        int count = 0;
        for (BigItemStack entry : packager.queuedExitingPackages) {
            count += Math.max(0, entry.count);
        }
        return count;
    }

    @Unique
    private static String fluidlogistics$resolveAddress(PackagerBlockEntity packager) {
        Level level = packager.getLevel();
        if (level != null && level.isClientSide) {
            String scannedAddress = fluidlogistics$findSignAddress(packager);
            if (!scannedAddress.isBlank()) {
                return scannedAddress;
            }
            if (packager.signBasedAddress.isBlank()) {
                return ((IPackagerOverrideData) packager).fluidlogistics$getClipboardAddress();
            }
        }
        if (packager.signBasedAddress.isBlank()) {
            return ((IPackagerOverrideData) packager).fluidlogistics$getClipboardAddress();
        }
        return packager.signBasedAddress;
    }

    @Unique
    private static String fluidlogistics$findSignAddress(PackagerBlockEntity packager) {
        for (Direction side : Iterate.directions) {
            String address = fluidlogistics$readSignAddress(packager, side);
            if (!address.isBlank()) {
                return address;
            }
        }
        return "";
    }

    @Unique
    private static String fluidlogistics$readSignAddress(PackagerBlockEntity packager, Direction side) {
        Level level = packager.getLevel();
        if (level == null) {
            return "";
        }

        BlockEntity blockEntity = level.getBlockEntity(packager.getBlockPos().relative(side));
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return "";
        }

        for (boolean front : Iterate.trueAndFalse) {
            SignText text = sign.getText(front);
            StringBuilder address = new StringBuilder();
            for (Component component : text.getMessages(false)) {
                String string = component.getString();
                if (!string.isBlank()) {
                    address.append(string.trim()).append(' ');
                }
            }
            if (address.length() > 0) {
                return address.toString().trim();
            }
        }

        return "";
    }

    @Override
    public boolean fluidlogistics$isManualOverrideLocked() {
        return fluidlogistics$manualOverrideLocked;
    }

    @Override
    public void fluidlogistics$setManualOverrideLocked(boolean locked) {
        fluidlogistics$manualOverrideLocked = locked;
    }

    @Override
    public String fluidlogistics$getClipboardAddress() {
        return fluidlogistics$clipboardAddress;
    }

    @Override
    public void fluidlogistics$setClipboardAddress(String address) {
        fluidlogistics$clipboardAddress = address == null ? "" : address;
    }
}

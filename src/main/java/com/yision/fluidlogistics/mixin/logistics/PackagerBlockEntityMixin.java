package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackager.PackagerGoggleInfo;
import com.yision.fluidlogistics.api.handpointer.PackagerAddresses;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.ResourcePackager;
import com.yision.fluidlogistics.api.packager.ResourcePackagers;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackagerBlockEntity.class)
public class PackagerBlockEntityMixin implements IPackagerOverrideData, IHaveGoggleInformation {

    @Unique
    private boolean fluidlogistics$manualOverrideLocked;
    @Unique
    private String fluidlogistics$clipboardAddress = "";
    @Unique
    private int fluidlogistics$queuedPackageCount;

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$unpackResourcePackage(ItemStack box, boolean simulate,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!PackageResources.isBootstrapped()
                || !PackageResources.inspectPackage(box).hasResources()) {
            return;
        }
        PackagerBlockEntity owner = (PackagerBlockEntity) (Object) this;
        ResourcePackager packager = ResourcePackagers.ownerOf(owner).orElse(null);
        cir.setReturnValue(packager != null && ResourcePackagers.unpack(packager, box, simulate));
    }

    @Inject(method = "getAvailableItems", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$getAvailableResources(CallbackInfoReturnable<InventorySummary> cir) {
        ResourcePackagers.ownerOf((PackagerBlockEntity) (Object) this)
                .ifPresent(packager -> cir.setReturnValue(ResourcePackagers.getAvailableResources(packager)));
    }

    @Inject(method = "attemptToSend", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$sendResourcePackage(
            List<PackagingRequest> queuedRequests, CallbackInfo ci) {
        ResourcePackager packager = ResourcePackagers
                .ownerOf((PackagerBlockEntity) (Object) this)
                .orElse(null);
        if (packager == null) {
            return;
        }
        ResourcePackagers.attemptToSend(packager, queuedRequests);
        ci.cancel();
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void fluidlogistics$writeOverrideData(CompoundTag compound, HolderLookup.Provider registries,
                                                  boolean clientPacket, CallbackInfo ci) {
        compound.putBoolean("FluidLogisticsManualOverrideLocked", fluidlogistics$manualOverrideLocked);
        compound.putString("FluidLogisticsClipboardAddress", fluidlogistics$clipboardAddress);
        compound.putInt("FluidLogisticsQueuedPackageCount",
            fluidlogistics$countQueuedPackages((PackagerBlockEntity) (Object) this));
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void fluidlogistics$readOverrideData(CompoundTag compound, HolderLookup.Provider registries,
                                                 boolean clientPacket, CallbackInfo ci) {
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
        if (!PackagerAddresses.isTarget(level, packager.getBlockPos())) {
            return;
        }

        if (!packager.signBasedAddress.isBlank() || fluidlogistics$clipboardAddress.isBlank()) {
            return;
        }

        packager.signBasedAddress = fluidlogistics$clipboardAddress;
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
    public String clipboardAddress() {
        return fluidlogistics$clipboardAddress;
    }

    @Override
    public void setClipboardAddress(String address) {
        fluidlogistics$clipboardAddress = address == null ? "" : address;
    }

    @Override
    public int fluidlogistics$getQueuedPackageCount() {
        return fluidlogistics$countCachedPackages((PackagerBlockEntity) (Object) this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        Level level = packager.getLevel();

        BlockState state = packager.getBlockState();
        boolean showsAddress = level != null && PackagerAddresses.isTarget(level, packager.getBlockPos());
        boolean isRepackager = packager instanceof RepackagerBlockEntity;
        boolean isLinkedToNetwork = state.hasProperty(PackagerBlock.LINKED) && state.getValue(PackagerBlock.LINKED);

        if (isRepackager) {
            int cachedPackageCount = fluidlogistics$countCachedPackages(packager);
            if (!fluidlogistics$manualOverrideLocked && cachedPackageCount <= 0) {
                return false;
            }
            PackagerGoggleInfo.addToTooltip(
                tooltip, "", fluidlogistics$manualOverrideLocked, true, false, cachedPackageCount);
            return true;
        }

        if (!showsAddress) {
            if (!fluidlogistics$manualOverrideLocked) {
                return false;
            }
            PackagerGoggleInfo.addPackagerManualOverrideOnlyToTooltip(tooltip);
            return true;
        }

        String address = packager.signBasedAddress;
        if (level != null && level.isClientSide) {
            String scannedAddress = fluidlogistics$findSignAddress(packager);
            if (!scannedAddress.isBlank()) {
                address = scannedAddress;
            } else if (address.isBlank()) {
                address = fluidlogistics$clipboardAddress;
            }
        } else if (address.isBlank()) {
            address = fluidlogistics$clipboardAddress;
        }

        PackagerGoggleInfo.addToTooltip(tooltip, address, fluidlogistics$manualOverrideLocked, isRepackager, isLinkedToNetwork);
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
}

package com.yision.fluidlogistics.mixin.logistics;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.goggle.PackagerGoggleInfo;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidInsertionHelper;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
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

    @Inject(method = "write", at = @At("RETURN"))
    private void fluidlogistics$writeOverrideData(CompoundTag compound, HolderLookup.Provider registries,
                                                  boolean clientPacket, CallbackInfo ci) {
        compound.putBoolean("FluidLogisticsManualOverrideLocked", fluidlogistics$manualOverrideLocked);
        compound.putString("FluidLogisticsClipboardAddress", fluidlogistics$clipboardAddress);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void fluidlogistics$readOverrideData(CompoundTag compound, HolderLookup.Provider registries,
                                                 boolean clientPacket, CallbackInfo ci) {
        fluidlogistics$manualOverrideLocked = compound.getBoolean("FluidLogisticsManualOverrideLocked");
        fluidlogistics$clipboardAddress = compound.getString("FluidLogisticsClipboardAddress");
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

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$unwrapCompressedTanksFirst(ItemStack box, boolean simulate,
                                                           CallbackInfoReturnable<Boolean> cir) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        if (packager.animationTicks > 0) {
            return;
        }

        Level level = packager.getLevel();
        if (level == null) {
            return;
        }

        ItemStackHandler contents = PackageItem.getContents(box);
        List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents);
        List<FluidStack> packageFluids = fluidlogistics$collectCompressedTankFluids(items);
        if (packageFluids.isEmpty()) {
            return;
        }

        Direction facing = packager.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        BlockPos targetPos = packager.getBlockPos().relative(facing.getOpposite());
        BlockState targetState = level.getBlockState(targetPos);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        if (!fluidlogistics$isCreateUnpackingTarget(level, targetPos, targetState, targetBlockEntity, facing)) {
            cir.setReturnValue(false);
            return;
        }

        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetState, targetBlockEntity, facing);
        if (fluidHandler == null) {
            cir.setReturnValue(false);
            return;
        }

        if (simulate) {
            if (!FluidInsertionHelper.canAcceptAll(targetBlockEntity, fluidHandler, packageFluids)) {
                cir.setReturnValue(false);
                return;
            }
        } else {
            if (!FluidInsertionHelper.insertAllOrNothing(targetBlockEntity, fluidHandler, packageFluids)) {
                cir.setReturnValue(false);
                return;
            }
        }

        items.removeIf(item -> item.getItem() instanceof CompressedTankItem);

        if (items.isEmpty()) {
            if (!simulate) {
                fluidlogistics$finishUnwrap(packager, box);
            }
            cir.setReturnValue(true);
            return;
        }

        PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
        UnpackingHandler handler = UnpackingHandler.REGISTRY.get(targetState);
        UnpackingHandler toUse = handler != null ? handler : UnpackingHandler.DEFAULT;
        boolean unpacked = toUse.unpack(level, targetPos, targetState, facing, items, orderContext, simulate);

        if (unpacked && !simulate) {
            fluidlogistics$finishUnwrap(packager, box);
        }

        cir.setReturnValue(unpacked);
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

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        Level level = packager.getLevel();

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

        BlockState state = packager.getBlockState();
        boolean isRepackager = packager instanceof RepackagerBlockEntity;
        boolean isLinkedToNetwork = state.hasProperty(PackagerBlock.LINKED) && state.getValue(PackagerBlock.LINKED);
        PackagerGoggleInfo.addToTooltip(tooltip, address, fluidlogistics$manualOverrideLocked, isRepackager, isLinkedToNetwork);
        return true;
    }

    // --- Private helpers ---

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

    @Unique
    private static boolean fluidlogistics$isCreateUnpackingTarget(Level level, BlockPos targetPos, BlockState targetState,
                                                                 BlockEntity targetBlockEntity, Direction facing) {
        if (UnpackingHandler.REGISTRY.get(targetState) != null) {
            return true;
        }

        return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState, targetBlockEntity, facing) != null;
    }

    @Unique
    private static List<FluidStack> fluidlogistics$collectCompressedTankFluids(List<ItemStack> items) {
        List<FluidStack> packageFluids = new ArrayList<>();
        for (ItemStack item : items) {
            if (!(item.getItem() instanceof CompressedTankItem)) {
                continue;
            }

            FluidStack fluid = CompressedTankItem.getFluid(item);
            for (int count = 0; count < item.getCount(); count++) {
                packageFluids.add(fluid.copy());
            }
        }
        return packageFluids;
    }

    @Unique
    private static void fluidlogistics$finishUnwrap(PackagerBlockEntity packager, ItemStack box) {
        packager.computerBehaviour.prepareComputerEvent(new PackageEvent(box, "package_received"));
        packager.previouslyUnwrapped = box;
        packager.animationInward = true;
        packager.animationTicks = PackagerBlockEntity.CYCLE;
        packager.notifyUpdate();
    }
}

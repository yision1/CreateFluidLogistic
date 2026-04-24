package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;
import java.util.Map;

import com.yision.fluidlogistics.util.FluidAmountHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidPromiseLimit;
import com.yision.fluidlogistics.util.IFluidRestockThreshold;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

    @Unique
    private static final String fluidlogistics$CAL_PROMISE_LIMIT_FIELD = "CAL$promiseLimit";

    @Unique
    private static final String fluidlogistics$CAL_ADDITIONAL_STOCK_FIELD = "CAL$AdditionalStock";

    @Unique
    private static final String fluidlogistics$CAL_REMAINING_ADDITIONAL_FIELD = "CAL$RemainingAdditional";

    @Shadow(remap = false)
    public boolean satisfied;

    @Shadow(remap = false)
    public boolean promisedSatisfied;

    @Shadow(remap = false)
    public boolean waitingForNetwork;

    @Shadow(remap = false)
    public boolean redstonePowered;

    @Shadow(remap = false)
    public String recipeAddress;

    @Shadow(remap = false)
    public java.util.UUID network;

    @Shadow(remap = false)
    public com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue restockerPromises;
    
    @Shadow(remap = false)
    public Map<FactoryPanelPosition, com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection> targetedBy;

    @Shadow(remap = false)
    private void sendEffect(com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition fromPos, boolean success) {
    }

    @Unique
    private void fluidlogistics$disableCalFactoryPanelState() {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (!FluidGaugeHelper.isVirtualFluidFilter(self)) {
            return;
        }

        fluidlogistics$setOptionalIntField(fluidlogistics$CAL_PROMISE_LIMIT_FIELD, -1);
        fluidlogistics$setOptionalIntField(fluidlogistics$CAL_ADDITIONAL_STOCK_FIELD, 0);
        fluidlogistics$setOptionalIntField(fluidlogistics$CAL_REMAINING_ADDITIONAL_FIELD, 0);
    }

    @Unique
    private void fluidlogistics$setOptionalIntField(String fieldName, int value) {
        try {
            java.lang.reflect.Field field = FactoryPanelBehaviour.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(this, value);
        } catch (ReflectiveOperationException e) {
        }
    }

    @Inject(
        method = "read",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$clearCalStateAfterRead(net.minecraft.nbt.CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "write",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$clearCalStateBeforeWrite(net.minecraft.nbt.CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "writeSafe",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$clearCalStateBeforeWriteSafe(net.minecraft.nbt.CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "tickStorageMonitor",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$clearCalStateBeforeStorageTick(CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "tickRequests",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$clearCalStateBeforeRequestTick(CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "tryRestock",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$clearCalStateBeforeRestock(CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "getRelevantSummary",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getRelevantSummaryForFluidPackager(CallbackInfoReturnable<InventorySummary> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        
        IFluidPackager fluidPackager = FluidGaugeHelper.getFluidPackager(panelBE);
        if (fluidPackager == null) {
            return;
        }
        
        cir.setReturnValue(fluidPackager.getAvailableItems());
    }

    @Inject(
        method = "getUnloadedLinks",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getUnloadedLinksForFluidPackager(CallbackInfoReturnable<Integer> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        
        if (cir.getReturnValue() != 1) {
            return;
        }
        
        IFluidPackager fluidPackager = FluidGaugeHelper.getFluidPackager(panelBE);
        if (fluidPackager != null) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "tryRestock",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$tryFluidRestock(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        
        PackagerBlockEntity packager = panelBE.getRestockedPackager();
        if (packager != null) {
            return;
        }
        
        IFluidPackager fluidPackager = FluidGaugeHelper.getFluidPackager(panelBE);
        if (fluidPackager == null) {
            return;
        }
        
        ItemStack item = self.getFilter();
        if (item.isEmpty()) {
            return;
        }
        
        int inStorage = self.getLevelInStorage();
        int promised = self.getPromised();
        int demand = FluidGaugeHelper.getRestockDemand(self);
        int shortage = demand - promised - inStorage;

        int threshold = FluidGaugeHelper.getEffectiveRestockThreshold(
            self instanceof IFluidRestockThreshold thresholdData ? thresholdData : null);

        if (shortage < threshold) {
            ci.cancel();
            return;
        }

        com.simibubi.create.content.logistics.packager.IdentifiedInventory identifiedInventory =
            fluidPackager.getIdentifiedInventory();

        int availableOnNetwork = LogisticsManager.getStockOf(network, item, identifiedInventory);
        if (availableOnNetwork == 0) {
            sendEffect(self.getPanelPosition(), false);
            ci.cancel();
            return;
        }
        
        int maxPackageContent;
        if (FluidGaugeHelper.isVirtualFluidFilter(item)) {
            maxPackageContent = Config.getFluidPerPackage();
        } else {
            maxPackageContent = item.getMaxStackSize() * 9;
        }

        int amountToOrder = Math.clamp(shortage, 0, maxPackageContent);
        if (self instanceof IFluidPromiseLimit promiseLimitData && promiseLimitData.fluidlogistics$hasPromiseLimit()) {
            amountToOrder = Math.min(amountToOrder, promiseLimitData.fluidlogistics$getPromiseLimit() - promised);
        }
        if (amountToOrder <= 0) {
            ci.cancel();
            return;
        }
        
        BigItemStack orderedItem = new BigItemStack(item, Math.min(amountToOrder, availableOnNetwork));
        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(List.of(orderedItem));
        
        sendEffect(self.getPanelPosition(), true);
        
        if (!LogisticsManager.broadcastPackageRequest(network, RequestType.RESTOCK, order,
            identifiedInventory, recipeAddress)) {
            ci.cancel();
            return;
        }
        
        restockerPromises.add(new RequestPromise(orderedItem));
        ci.cancel();
    }

    @Inject(
        method = "createBoard",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$modifyBoardLabels(Player player, BlockHitResult hitResult, 
            CallbackInfoReturnable<ValueSettingsBoard> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        if (FluidGaugeHelper.isVirtualFluidFilter(filter)) {
            ValueSettingsBoard original = cir.getReturnValue();
            ValueSettingsBoard fluidBoard = new ValueSettingsBoard(
                CreateLang.translate("factory_panel.target_amount").component(),
                100,
                10,
                java.util.List.of(
                    CreateLang.text("mB").component(),
                    CreateLang.text("B").component()
                ),
                original.formatter()
            );
            cir.setReturnValue(fluidBoard);
        }
    }

    @Inject(
        method = "formatValue",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$formatFluidValue(ValueSettingsBehaviour.ValueSettings value, 
            CallbackInfoReturnable<MutableComponent> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        if (FluidGaugeHelper.isVirtualFluidFilter(filter)) {
            if (value.value() == 0) {
                if (value.row() == 1) {
                    cir.setReturnValue(Component.literal("1B"));
                } else {
                    cir.setReturnValue(CreateLang.translateDirect("gui.factory_panel.inactive"));
                }
            } else {
                int displayValue = value.row() == 1
                    ? Math.min(100, Math.max(1, value.value()))
                    : Math.max(0, value.value()) * 10;
                boolean useBuckets = value.row() == 1;
                String unit = useBuckets ? "B" : "mB";
                cir.setReturnValue(Component.literal(displayValue + unit));
            }
        }
    }

    @Unique
    private static ThreadLocal<Boolean> fluidlogistics$needsConversion = ThreadLocal.withInitial(() -> false);
    
    @Unique
    private static ThreadLocal<Boolean> fluidlogistics$useBucketsMode = ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "setValueSettings",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$beforeSetValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, 
            boolean ctrlDown, CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        if (FluidGaugeHelper.isVirtualFluidFilter(filter)) {
            fluidlogistics$needsConversion.set(true);
            fluidlogistics$useBucketsMode.set(settings.row() == 1);
        } else {
            fluidlogistics$needsConversion.set(false);
            fluidlogistics$useBucketsMode.set(false);
        }
    }

    @ModifyExpressionValue(
        method = "setValueSettings",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsBehaviour$ValueSettings;value()I",
            remap = false
        ),
        remap = false
    )
    private int fluidlogistics$modifySettingsValue(int original) {
        if (fluidlogistics$needsConversion.get()) {
            if (fluidlogistics$useBucketsMode.get()) {
                int clampedBuckets = Math.clamp(original, 0, 100);
                if (clampedBuckets == 0) {
                    return 1000;
                }
                return clampedBuckets * 1000;
            } else {
                return original * 10;
            }
        }
        return original;
    }

    @Inject(
        method = "getValueSettings",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$onGetValueSettings(CallbackInfoReturnable<ValueSettingsBehaviour.ValueSettings> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        if (FluidGaugeHelper.isVirtualFluidFilter(filter)) {
            int count = self.getAmount();
            boolean upTo = self.upTo;
            boolean useBuckets = count >= 1000;
            int displayValue;
            if (useBuckets) {
                displayValue = count <= 1000 ? 0 : Math.clamp(count / 1000, 0, 100);
            } else {
                displayValue = count / 10;
            }
            cir.setReturnValue(new ValueSettingsBehaviour.ValueSettings(useBuckets ? 1 : (upTo ? 0 : 1), displayValue));
        }
    }

    @Inject(
        method = "getCountLabelForValueBox",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$onGetCountLabelForValueBox(CallbackInfoReturnable<MutableComponent> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        if (!FluidGaugeHelper.isVirtualFluidFilter(filter)) {
            return;
        }
        
        if (filter.isEmpty()) {
            cir.setReturnValue(Component.empty());
            return;
        }
        
        boolean waitingForNetwork = self.waitingForNetwork;
        if (waitingForNetwork) {
            cir.setReturnValue(Component.literal("?"));
            return;
        }
        
        int levelInStorage = self.getLevelInStorage();
        int count = self.getAmount();
        boolean satisfied = self.satisfied;
        boolean promisedSatisfied = self.promisedSatisfied;

        if (count == 0){
            cir.setReturnValue(CreateLang.text("  " + FluidAmountHelper.format(levelInStorage))
                    .color(0xF1EFE8)
                    .component());
            return;
        }

        cir.setReturnValue(CreateLang.text("   " + FluidAmountHelper.format(levelInStorage))
                .color(satisfied ? 0xD7FFA8 : promisedSatisfied ? 0xffcd75 : 0xFFBFA8)
                .add(CreateLang.text("/")
                        .style(ChatFormatting.WHITE))
                .add(CreateLang.text(FluidAmountHelper.format(count) + "  ")
                        .color(0xF1EFE8))
                .component());
    }

    @Inject(
        method = "getLabel",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$onGetLabel(CallbackInfoReturnable<MutableComponent> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        
        if (!FluidGaugeHelper.isVirtualFluidFilter(filter)) {
            return;
        }
        
        FluidStack fluid = CompressedTankItem.getFluid(filter);
        if (fluid.isEmpty()) {
            return;
        }
        
        if (!targetedBy.isEmpty() && self.getAmount() == 0) {
            cir.setReturnValue(CreateLang.translate("gui.factory_panel.no_target_amount_set")
                .style(ChatFormatting.RED)
                .component());
            return;
        }
        
        if (self.isMissingAddress()) {
            cir.setReturnValue(CreateLang.translate("gui.factory_panel.address_missing")
                .style(ChatFormatting.RED)
                .component());
            return;
        }
        
        if (waitingForNetwork) {
            cir.setReturnValue(CreateLang.translate("factory_panel.some_links_unloaded")
                .component());
            return;
        }
        
        String fluidName = fluid.getHoverName().getString();
        
        if (self.getAmount() == 0 || targetedBy.isEmpty()) {
            cir.setReturnValue(fluid.getHoverName().plainCopy());
            return;
        }
        
        if (redstonePowered) {
            fluidName += " " + CreateLang.translate("factory_panel.redstone_paused").string();
        } else if (!satisfied) {
            fluidName += " " + CreateLang.translate("factory_panel.in_progress").string();
        }
        
        cir.setReturnValue(CreateLang.text(fluidName).component());
    }

    @Inject(
        method = "getFrogAddress",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getFluidFrogAddress(CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        
        if (!panelBE.restocker) {
            return;
        }
        
        IFluidPackager fluidPackager = FluidGaugeHelper.getFluidPackager(panelBE);
        if (fluidPackager == null) {
            return;
        }
        
        if (!(fluidPackager instanceof net.minecraft.world.level.block.entity.BlockEntity be)) {
            return;
        }
        
        if (be.getLevel().getBlockEntity(be.getBlockPos().above()) instanceof FrogportBlockEntity fpbe) {
            if (fpbe.addressFilter != null && !fpbe.addressFilter.isBlank()) {
                cir.setReturnValue(fpbe.addressFilter + "");
            }
        }
    }
}

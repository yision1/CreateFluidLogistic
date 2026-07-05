package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

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
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidPromiseLimit;
import com.yision.fluidlogistics.util.IFluidRestockThreshold;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin {
    @Unique
    private static final String fluidlogistics$CAL_PROMISE_LIMIT_FIELD = "CAL$promiseLimit";

    @Unique
    private static final String fluidlogistics$CAL_ADDITIONAL_STOCK_FIELD = "CAL$AdditionalStock";

    @Unique
    private static final String fluidlogistics$CAL_REMAINING_ADDITIONAL_FIELD = "CAL$RemainingAdditional";

    @Shadow public boolean satisfied;
    @Shadow public boolean promisedSatisfied;
    @Shadow public boolean waitingForNetwork;
    @Shadow public boolean redstonePowered;
    @Shadow public String recipeAddress;
    @Shadow public UUID network;
    @Shadow public RequestPromiseQueue restockerPromises;
    @Shadow public Map<FactoryPanelPosition, FactoryPanelConnection> targetedBy;

    @Shadow public abstract FactoryPanelBlockEntity panelBE();
    @Shadow public abstract FactoryPanelPosition getPanelPosition();
    @Shadow public abstract int getLevelInStorage();
    @Shadow public abstract int getPromised();
    @Shadow public abstract boolean isMissingAddress();

    @Shadow
    private void sendEffect(FactoryPanelPosition fromPos, boolean success) {}
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

    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private void fluidlogistics$clearCalStateAfterRead(net.minecraft.nbt.CompoundTag nbt, boolean clientPacket,
        CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(method = "write", at = @At("HEAD"), remap = false)
    private void fluidlogistics$clearCalStateBeforeWrite(net.minecraft.nbt.CompoundTag nbt, boolean clientPacket,
        CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(method = "writeSafe", at = @At("HEAD"), remap = false)
    private void fluidlogistics$clearCalStateBeforeWriteSafe(net.minecraft.nbt.CompoundTag nbt, CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(method = "tickStorageMonitor", at = @At("HEAD"), remap = false)
    private void fluidlogistics$clearCalStateBeforeStorageTick(CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(method = "tickRequests", at = @At("HEAD"), remap = false)
    private void fluidlogistics$clearCalStateBeforeRequestTick(CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Inject(
        method = "tickRequests",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;resetTimer()V",
            shift = At.Shift.AFTER
        ),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$limitNonRestockFluidPromises(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (panelBE().restocker || !FluidGaugeHelper.isVirtualFluidFilter(self)) {
            return;
        }

        if (!(self instanceof IFluidPromiseLimit promiseLimitData)
            || !promiseLimitData.fluidlogistics$hasPromiseLimit()) {
            return;
        }

        int limit = promiseLimitData.fluidlogistics$getPromiseLimit();
        int nextPromise = Math.max(1, self.recipeOutput);
        if (limit <= 0 || self.getPromised() + nextPromise > limit) {
            ci.cancel();
        }
    }

    @Inject(method = "tryRestock", at = @At("HEAD"), remap = false)
    private void fluidlogistics$clearCalStateBeforeRestock(CallbackInfo ci) {
        fluidlogistics$disableCalFactoryPanelState();
    }

    @Unique
    private static final ThreadLocal<Boolean> fluidlogistics$needsConversion = ThreadLocal.withInitial(() -> false);
    @Unique
    private static final ThreadLocal<Boolean> fluidlogistics$useBucketsMode = ThreadLocal.withInitial(() -> false);

    @Unique
    private static boolean fluidlogistics$isVirtualTank(ItemStack filter) {
        return filter.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(filter);
    }

    @Inject(method = "getRelevantSummary", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$getRelevantSummaryForFluidPackager(CallbackInfoReturnable<InventorySummary> cir) {
        IFluidPackager fluidPackager = fluidlogistics$getFluidPackager(panelBE());
        if (fluidPackager != null) {
            cir.setReturnValue(fluidPackager.getAvailableItems());
        }
    }

    @Inject(method = "getUnloadedLinks", at = @At("RETURN"), cancellable = true)
    private void fluidlogistics$getUnloadedLinksForFluidPackager(CallbackInfoReturnable<Integer> cir) {
        if (panelBE().restocker && cir.getReturnValue() == 1 && fluidlogistics$getFluidPackager(panelBE()) != null) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "tryRestock", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$tryFluidRestock(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();

        PackagerBlockEntity packager = panelBE.getRestockedPackager();
        if (packager != null && !(packager instanceof IFluidPackager)) {
            return;
        }

        IFluidPackager fluidPackager = fluidlogistics$getFluidPackager(panelBE);
        if (fluidPackager == null) {
            return;
        }

        ItemStack item = fluidlogistics$getFilter();
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

        IdentifiedInventory identifiedInventory = fluidPackager.getIdentifiedInventory();
        int availableOnNetwork = LogisticsManager.getStockOf(network, item, identifiedInventory);
        if (availableOnNetwork == 0) {
            sendEffect(self.getPanelPosition(), false);
            ci.cancel();
            return;
        }

        int amountToOrder = Math.min(shortage, availableOnNetwork);
        amountToOrder = Math.min(amountToOrder, FluidGaugeHelper.getMaxFluidRequestPerBatch());
        if (self instanceof IFluidPromiseLimit promiseLimitData && promiseLimitData.fluidlogistics$hasPromiseLimit()) {
            amountToOrder = Math.min(amountToOrder, promiseLimitData.fluidlogistics$getPromiseLimit() - promised);
        }
        if (amountToOrder <= 0) {
            ci.cancel();
            return;
        }

        BigItemStack orderedItem = new BigItemStack(item, amountToOrder);
        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(List.of(orderedItem));

        sendEffect(self.getPanelPosition(), true);
        if (!LogisticsManager.broadcastPackageRequest(network, RequestType.RESTOCK, order, identifiedInventory,
            recipeAddress)) {
            ci.cancel();
            return;
        }

        restockerPromises.add(new RequestPromise(orderedItem));
        ci.cancel();
    }

    @Inject(method = "createBoard", at = @At("RETURN"), cancellable = true)
    private void fluidlogistics$modifyBoardLabels(Player player, BlockHitResult hitResult,
        CallbackInfoReturnable<ValueSettingsBoard> cir) {
        if (!fluidlogistics$isVirtualTank(fluidlogistics$getFilter())) {
            return;
        }

        ValueSettingsBoard original = cir.getReturnValue();
        cir.setReturnValue(new ValueSettingsBoard(CreateLang.translate("factory_panel.target_amount").component(), 100,
            10, List.of(CreateLang.text("mB").component(), CreateLang.text("B").component()), original.formatter()));
    }

    @Inject(method = "formatValue", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$formatFluidValue(ValueSettings value, CallbackInfoReturnable<MutableComponent> cir) {
        if (!fluidlogistics$isVirtualTank(fluidlogistics$getFilter())) {
            return;
        }

        String formatted = FluidAmountHelper.formatFactoryGaugeValueSetting(value.row(), value.value());
        cir.setReturnValue(formatted == null
            ? CreateLang.translateDirect("gui.factory_panel.inactive")
            : Component.literal(formatted));
    }

    @Inject(method = "setValueSettings", at = @At("HEAD"))
    private void fluidlogistics$beforeSetValueSettings(Player player, ValueSettings settings, boolean ctrlDown,
        CallbackInfo ci) {
        boolean isFluid = fluidlogistics$isVirtualTank(fluidlogistics$getFilter());
        fluidlogistics$needsConversion.set(isFluid);
        fluidlogistics$useBucketsMode.set(isFluid && settings.row() == 1);
    }

    @ModifyExpressionValue(
        method = "setValueSettings",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsBehaviour$ValueSettings;value()I",
            remap = false
        )
    )
    private int fluidlogistics$modifySettingsValue(int original) {
        if (!fluidlogistics$needsConversion.get()) {
            return original;
        }
        return FluidAmountHelper.toFactoryGaugeAmount(fluidlogistics$useBucketsMode.get() ? 1 : 0, original);
    }

    @Inject(method = "getValueSettings", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$onGetValueSettings(CallbackInfoReturnable<ValueSettings> cir) {
        if (!fluidlogistics$isVirtualTank(fluidlogistics$getFilter())) {
            return;
        }

        int count = fluidlogistics$getAmount();
        boolean useBuckets = count >= FluidAmountHelper.MB_PER_BUCKET;
        int displayValue = FluidAmountHelper.toFactoryGaugeValueSetting(count);
        cir.setReturnValue(new ValueSettings(useBuckets ? 1 : (fluidlogistics$isUpTo() ? 0 : 1), displayValue));
    }

    @Inject(method = "getCountLabelForValueBox", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$onGetCountLabelForValueBox(CallbackInfoReturnable<MutableComponent> cir) {
        ItemStack filter = fluidlogistics$getFilter();
        if (!fluidlogistics$isVirtualTank(filter)) {
            return;
        }

        if (filter.isEmpty()) {
            cir.setReturnValue(Component.empty());
            return;
        }
        if (waitingForNetwork) {
            cir.setReturnValue(Component.literal("?"));
            return;
        }

        int levelInStorage = getLevelInStorage();
        int count = fluidlogistics$getAmount();

        if (count == 0) {
            cir.setReturnValue(CreateLang.text("  " + FluidAmountHelper.format(levelInStorage))
                .color(0xF1EFE8)
                .component());
            return;
        }

        cir.setReturnValue(CreateLang.text("   " + FluidAmountHelper.format(levelInStorage))
            .color(satisfied ? 0xD7FFA8 : promisedSatisfied ? 0xffcd75 : 0xFFBFA8)
            .add(CreateLang.text("/").style(ChatFormatting.WHITE))
            .add(CreateLang.text(FluidAmountHelper.format(count) + "  ").color(0xF1EFE8))
            .component());
    }

    @Inject(method = "getLabel", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$onGetLabel(CallbackInfoReturnable<MutableComponent> cir) {
        ItemStack filter = fluidlogistics$getFilter();
        if (!fluidlogistics$isVirtualTank(filter)) {
            return;
        }

        FluidStack fluid = CompressedTankItem.getFluid(filter);

        if (!targetedBy.isEmpty() && fluidlogistics$getAmount() == 0) {
            cir.setReturnValue(CreateLang.translate("gui.factory_panel.no_target_amount_set")
                .style(ChatFormatting.RED)
                .component());
            return;
        }
        if (isMissingAddress()) {
            cir.setReturnValue(CreateLang.translate("gui.factory_panel.address_missing")
                .style(ChatFormatting.RED)
                .component());
            return;
        }
        if (waitingForNetwork) {
            cir.setReturnValue(CreateLang.translate("factory_panel.some_links_unloaded").component());
            return;
        }
        if (fluidlogistics$getAmount() == 0 || targetedBy.isEmpty()) {
            cir.setReturnValue(fluid.getDisplayName().plainCopy());
            return;
        }

        String fluidName = fluid.getDisplayName().getString();
        if (redstonePowered) {
            fluidName += " " + CreateLang.translate("factory_panel.redstone_paused").string();
        } else if (!satisfied) {
            fluidName += " " + CreateLang.translate("factory_panel.in_progress").string();
        }
        cir.setReturnValue(CreateLang.text(fluidName).component());
    }

    @Inject(method = "getFrogAddress", at = @At("RETURN"), cancellable = true)
    private void fluidlogistics$getFluidFrogAddress(CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null || !panelBE().restocker) {
            return;
        }

        IFluidPackager fluidPackager = fluidlogistics$getFluidPackager(panelBE());
        if (!(fluidPackager instanceof BlockEntity be) || be.getLevel() == null) {
            return;
        }

        if (be.getLevel().getBlockEntity(be.getBlockPos().above()) instanceof FrogportBlockEntity fpbe
            && fpbe.addressFilter != null && !fpbe.addressFilter.isBlank()) {
            cir.setReturnValue(fpbe.addressFilter + "");
        }
    }

    @Unique
    @Nullable
    private static IFluidPackager fluidlogistics$getFluidPackager(FactoryPanelBlockEntity panelBE) {
        if (panelBE.getLevel() == null) {
            return null;
        }

        BlockState state = panelBE.getBlockState();
        if (!com.simibubi.create.AllBlocks.FACTORY_GAUGE.has(state)) {
            return null;
        }

        BlockPos packagerPos = panelBE.getBlockPos()
            .relative(FactoryPanelBlock.connectedDirection(state).getOpposite());
        if (!panelBE.getLevel().isLoaded(packagerPos)) {
            return null;
        }

        BlockEntity be = panelBE.getLevel().getBlockEntity(packagerPos);
        return be instanceof IFluidPackager fluidPackager ? fluidPackager : null;
    }

    @Unique
    private ItemStack fluidlogistics$getFilter() {
        return ((FactoryPanelBehaviour) (Object) this).getFilter();
    }

    @Unique
    private int fluidlogistics$getAmount() {
        return ((FactoryPanelBehaviour) (Object) this).getAmount();
    }

    @Unique
    private boolean fluidlogistics$isUpTo() {
        return ((FilteringBehaviour) (Object) this).upTo;
    }
}


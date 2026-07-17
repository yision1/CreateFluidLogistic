package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
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
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.api.packager.ResourcePackager;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.api.packager.PackageResourceType;
import com.yision.fluidlogistics.api.packager.ResourcePackagers;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourceRestockSettings;
import com.yision.fluidlogistics.compat.cal.CalFactoryPanelCompat;
import com.yision.fluidlogistics.util.ResourceGaugeHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin extends FilteringBehaviour
    implements ResourceRestockSettings {

    public FactoryPanelBehaviourMixin(SmartBlockEntity be, ValueBoxTransform slot) {
        super(be, slot);
    }

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
    public RequestPromiseQueue restockerPromises;

    @Shadow(remap = false)
    public Map<FactoryPanelPosition, FactoryPanelConnection> targetedBy;

    @Shadow(remap = false)
    private void sendEffect(FactoryPanelPosition fromPos, boolean success) {
    }

    @Unique
    private void fluidlogistics$disableCalFactoryPanelState() {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (!ResourceGaugeHelper.hasConfigurableSettings(self)) {
            return;
        }

        CalFactoryPanelCompat.resetFluidPanelState(self);
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
        method = "tickRequests",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;resetTimer()V",
            shift = At.Shift.AFTER
        ),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$limitNonRestockResourcePromises(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        PackageResourceDisplay.FactoryPanelRestockPolicy policy = ResourceGaugeHelper.policy(self);
        if (self.panelBE().restocker || !policy.configurablePromiseLimit()) {
            return;
        }

        if (!fluidlogistics$hasPromiseLimit()) {
            return;
        }

        int capacity = policy.remainingPromiseCapacity(fluidlogistics$promiseLimit, self.getPromised());
        int nextPromise = Math.max(1, self.recipeOutput);
        if (capacity < nextPromise) {
            ci.cancel();
        }
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
    private void fluidlogistics$getRelevantSummaryForResourcePackager(CallbackInfoReturnable<InventorySummary> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        if (!PackageResources.isBootstrapped()) {
            return;
        }
        ItemStack filter = self.getFilter();
        PackageResourceType type = PackageResources.findType(filter).orElse(null);
        PackagerBlockEntity owner = panelBE.getRestockedPackager();
        ResourcePackager packager = ResourcePackagers.ownerOf(owner).orElse(null);
        if (type == null || packager == null) {
            return;
        }
        ItemStack normalizedKey = type.normalizeKey(filter.copy());
        if (ResourcePackagers.supports(packager, type, normalizedKey)) {
            cir.setReturnValue(ResourcePackagers.getAvailableResources(packager));
        }
    }

    @Inject(
        method = "getUnloadedLinks",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getUnloadedLinksForResourceRestocker(CallbackInfoReturnable<Integer> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();

        if (!panelBE.restocker || cir.getReturnValue() != 1) {
            return;
        }

        if (ResourcePackagers.ownerOf(panelBE.getRestockedPackager()).isPresent()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "tryRestock",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$tryResourceRestock(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        if (!PackageResources.isBootstrapped()) {
            return;
        }

        ItemStack filter = self.getFilter();
        PackageResourceType resourceType = PackageResources.findType(filter).orElse(null);
        PackagerBlockEntity owner = panelBE.getRestockedPackager();
        ResourcePackager resourcePackager = ResourcePackagers.ownerOf(owner).orElse(null);
        if (resourceType == null || resourcePackager == null) {
            return;
        }
        ItemStack item = resourceType.normalizeKey(filter.copy());
        if (!ResourcePackagers.supports(resourcePackager, resourceType, item.copy())) {
            return;
        }

        PackageResourceDisplay display = resourceType.display();
        PackageResourceDisplay.FactoryPanelRestockPolicy policy =
                display.factoryPanelRestockPolicy(item.copy());
        int inStorage = self.getLevelInStorage();
        int promised = self.getPromised();
        int demand = policy.restockDemand(self.getAmount(), fluidlogistics$remainingAdditionalStock);
        long shortageValue = (long) demand - promised - inStorage;
        int threshold = policy.effectiveThreshold(fluidlogistics$restockThreshold);

        if (shortageValue < threshold) {
            ci.cancel();
            return;
        }

        IdentifiedInventory identifiedInventory = owner.targetInventory == null
                ? null
                : owner.targetInventory.getIdentifiedInventory();
        int availableOnNetwork = LogisticsManager.getStockOf(network, item, identifiedInventory);
        if (availableOnNetwork == 0) {
            sendEffect(self.getPanelPosition(), false);
            ci.cancel();
            return;
        }

        int shortage = (int) Math.min(BigItemStack.INF, shortageValue);
        int amountToOrder = Math.min(shortage, availableOnNetwork);
        amountToOrder = Math.min(amountToOrder, policy.maxRequestPerBatch());
        amountToOrder = Math.min(amountToOrder,
                policy.remainingPromiseCapacity(fluidlogistics$promiseLimit, promised));
        if (amountToOrder <= 0) {
            ci.cancel();
            return;
        }
        
        BigItemStack orderedItem = new BigItemStack(item, amountToOrder);
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
        PackageResourceDisplay display = PackageResources.displayOf(filter).orElse(null);
        if (display == null) {
            return;
        }
        ValueSettingsBoard original = cir.getReturnValue();
        List<PackageResourceDisplay.FactoryPanelUnit> units = display.factoryPanelUnits(filter);
        cir.setReturnValue(new ValueSettingsBoard(
            CreateLang.translate("factory_panel.target_amount").component(),
            display.factoryPanelMaxValue(filter),
            display.factoryPanelMilestoneInterval(filter),
            units.stream().<Component>map(unit -> Component.literal(unit.label())).toList(),
            original.formatter()
        ));
    }

    @Inject(
        method = "formatValue",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$formatResourceValue(ValueSettingsBehaviour.ValueSettings value,
            CallbackInfoReturnable<MutableComponent> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack filter = self.getFilter();
        PackageResourceDisplay display = PackageResources.displayOf(filter).orElse(null);
        if (display == null) {
            return;
        }
        if (value.value() == 0) {
            cir.setReturnValue(CreateLang.translateDirect("gui.factory_panel.inactive"));
            return;
        }
        List<PackageResourceDisplay.FactoryPanelUnit> units = display.factoryPanelUnits(filter);
        int row = Math.max(0, Math.min(units.size() - 1, value.row()));
        int displayedValue = display.factoryPanelDisplayedValue(filter, row, value.value());
        cir.setReturnValue(Component.literal(displayedValue + units.get(row).label()));
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
    private int fluidlogistics$modifySettingsValue(int original, Player player,
            ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        ItemStack key = self.getFilter();
        return PackageResources.displayOf(key)
                .map(display -> display.factoryPanelAmount(key, settings.row(), original))
                .orElse(original);
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
        PackageResourceDisplay display = PackageResources.displayOf(filter).orElse(null);
        if (display != null) {
            int amount = self.getAmount();
            int unitCount = display.factoryPanelUnits(filter).size();
            int row = self.upTo || unitCount == 1
                    ? 0
                    : Math.max(1, Math.min(unitCount - 1, display.factoryPanelRow(filter, amount)));
            int value = display.factoryPanelValue(filter, row, amount);
            cir.setReturnValue(new ValueSettingsBehaviour.ValueSettings(row, value));
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
        int levelInStorage = self.getLevelInStorage();
        var levelText = PackageResources.formatAmount(
                filter, levelInStorage, PackageResourceDisplay.Format.COMPACT);
        if (levelText.isEmpty()) {
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
        
        int count = self.getAmount();
        boolean satisfied = self.satisfied;
        boolean promisedSatisfied = self.promisedSatisfied;

        if (count == 0){
            cir.setReturnValue(CreateLang.text("  " + levelText.orElseThrow())
                    .color(0xF1EFE8)
                    .component());
            return;
        }

        String countText = PackageResources.formatAmount(
                filter, count, PackageResourceDisplay.Format.COMPACT).orElse(Integer.toString(count));
        cir.setReturnValue(CreateLang.text("   " + levelText.orElseThrow())
                .color(satisfied ? 0xD7FFA8 : promisedSatisfied ? 0xffcd75 : 0xFFBFA8)
                .add(CreateLang.text("/")
                        .style(ChatFormatting.WHITE))
                .add(CreateLang.text(countText + "  ")
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
        Component resourceName = PackageResources.nameOf(filter).orElse(null);
        if (resourceName == null) {
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
        
        if (self.getAmount() == 0 || targetedBy.isEmpty()) {
            cir.setReturnValue(resourceName.copy());
            return;
        }
        String label = resourceName.getString();
        if (redstonePowered) {
            label += " " + CreateLang.translate("factory_panel.redstone_paused").string();
        } else if (!satisfied) {
            label += " " + CreateLang.translate("factory_panel.in_progress").string();
        }
        cir.setReturnValue(CreateLang.text(label).component());
    }

    @Inject(
        method = "getFrogAddress",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getResourceFrogAddress(CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = self.panelBE();
        
        if (!panelBE.restocker) {
            return;
        }
        
        PackagerBlockEntity owner = panelBE.getRestockedPackager();
        if (ResourcePackagers.ownerOf(owner).isEmpty()) {
            return;
        }

        if (owner.getLevel().getBlockEntity(owner.getBlockPos().above()) instanceof FrogportBlockEntity fpbe) {
            if (fpbe.addressFilter != null && !fpbe.addressFilter.isBlank()) {
                cir.setReturnValue(fpbe.addressFilter + "");
            }
        }
    }


    @Shadow(remap = false)
    private int lastReportedLevelInStorage;

    @Shadow(remap = false)
    private int lastReportedPromises;

    @Shadow(remap = false)
    private int lastReportedUnloadedLinks;

    @Shadow(remap = false)
    private int timer;

    @Shadow(remap = false)
    private void notifyRedstoneOutputs() {
    }

    @Unique
    private static final String fluidlogistics$RESTOCK_THRESHOLD_KEY = ResourceGaugeHelper.RESTOCK_THRESHOLD_KEY;

    @Unique
    private static final String fluidlogistics$PROMISE_LIMIT_KEY = ResourceGaugeHelper.PROMISE_LIMIT_KEY;

    @Unique
    private static final String fluidlogistics$ADDITIONAL_STOCK_KEY = ResourceGaugeHelper.ADDITIONAL_STOCK_KEY;

    @Unique
    private static final String fluidlogistics$REMAINING_ADDITIONAL_STOCK_KEY =
        ResourceGaugeHelper.REMAINING_ADDITIONAL_STOCK_KEY;

    @Unique
    private static final int fluidlogistics$DEFAULT_RESTOCK_THRESHOLD = ResourceGaugeHelper.DEFAULT_RESTOCK_THRESHOLD;

    @Unique
    private static final int fluidlogistics$DEFAULT_PROMISE_LIMIT = ResourceGaugeHelper.DEFAULT_PROMISE_LIMIT;

    @Unique
    private static final int fluidlogistics$DEFAULT_ADDITIONAL_STOCK = ResourceGaugeHelper.DEFAULT_ADDITIONAL_STOCK;

    @Unique
    private int fluidlogistics$restockThreshold = fluidlogistics$DEFAULT_RESTOCK_THRESHOLD;

    @Unique
    private int fluidlogistics$promiseLimit = fluidlogistics$DEFAULT_PROMISE_LIMIT;

    @Unique
    private int fluidlogistics$additionalStock = fluidlogistics$DEFAULT_ADDITIONAL_STOCK;

    @Unique
    private int fluidlogistics$remainingAdditionalStock = fluidlogistics$DEFAULT_ADDITIONAL_STOCK;

    @Override
    public int fluidlogistics$getRestockThreshold() {
        return fluidlogistics$restockThreshold;
    }

    @Override
    public void fluidlogistics$setRestockThreshold(int threshold) {
        fluidlogistics$restockThreshold = ResourceGaugeHelper
                .policy((FactoryPanelBehaviour) (Object) this)
                .clampThreshold(threshold);
    }

    @Override
    public int fluidlogistics$getPromiseLimit() {
        return fluidlogistics$promiseLimit;
    }

    @Override
    public void fluidlogistics$setPromiseLimit(int limit) {
        fluidlogistics$promiseLimit = ResourceGaugeHelper
                .policy((FactoryPanelBehaviour) (Object) this)
                .clampPromiseLimit(limit);
    }

    @Override
    public boolean fluidlogistics$hasPromiseLimit() {
        return fluidlogistics$promiseLimit >= 0;
    }

    @Override
    public int fluidlogistics$getAdditionalStock() {
        return fluidlogistics$additionalStock;
    }

    @Override
    public void fluidlogistics$setAdditionalStock(int amount) {
        fluidlogistics$additionalStock = ResourceGaugeHelper
                .policy((FactoryPanelBehaviour) (Object) this)
                .clampAdditionalStock(amount);
        if (fluidlogistics$remainingAdditionalStock > fluidlogistics$additionalStock) {
            fluidlogistics$remainingAdditionalStock = fluidlogistics$additionalStock;
        }
        if (fluidlogistics$shouldApplyResourceRestock() && !satisfied
            && fluidlogistics$remainingAdditionalStock <= 0
            && fluidlogistics$additionalStock > 0) {
            fluidlogistics$remainingAdditionalStock = fluidlogistics$additionalStock;
        }
    }

    @Override
    public boolean fluidlogistics$hasAdditionalStock() {
        return fluidlogistics$additionalStock > 0;
    }

    @Override
    public int fluidlogistics$getRemainingAdditionalStock() {
        return fluidlogistics$remainingAdditionalStock;
    }

    @Inject(
        method = "tickStorageMonitor",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$tickResourceRestockStorageMonitor(CallbackInfo ci) {
        if (!fluidlogistics$shouldApplyResourceRestock()) {
            return;
        }
        ci.cancel();

        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        int unloadedLinkCount = self.getUnloadedLinks();
        int inStorage = self.getLevelInStorage();
        if (fluidlogistics$remainingAdditionalStock > 0 && lastReportedLevelInStorage > inStorage) {
            fluidlogistics$remainingAdditionalStock = Math.max(0,
                fluidlogistics$remainingAdditionalStock - (lastReportedLevelInStorage - inStorage));
        }

        PackageResourceDisplay.FactoryPanelRestockPolicy policy = ResourceGaugeHelper.policy(self);
        int threshold = policy.effectiveThreshold(fluidlogistics$restockThreshold);
        int promised = self.getPromised();
        int demand = policy.restockDemand(self.getAmount(), fluidlogistics$remainingAdditionalStock);

        boolean previousSatisfied = satisfied;
        boolean shouldSatisfy = demand - inStorage < threshold;
        boolean shouldPromiseSatisfy = demand - inStorage - promised < threshold;
        boolean shouldWait = unloadedLinkCount > 0;

        if (previousSatisfied && !shouldSatisfy && timer > 1) {
            timer = 1;
        }

        if (lastReportedLevelInStorage == inStorage
                && lastReportedPromises == promised
                && lastReportedUnloadedLinks == unloadedLinkCount
                && satisfied == shouldSatisfy
                && promisedSatisfied == shouldPromiseSatisfy
                && waitingForNetwork == shouldWait) {
            return;
        }

        if (!satisfied && shouldSatisfy && demand > 0) {
            AllSoundEvents.CONFIRM.playOnServer(self.getWorld(), self.getPos(), 0.075f, 1f);
            AllSoundEvents.CONFIRM_2.playOnServer(self.getWorld(), self.getPos(), 0.125f, 0.575f);
        }

        boolean notifyOutputs = satisfied != shouldSatisfy;
        lastReportedLevelInStorage = inStorage;
        lastReportedPromises = promised;
        lastReportedUnloadedLinks = unloadedLinkCount;
        satisfied = shouldSatisfy;
        promisedSatisfied = shouldPromiseSatisfy;
        waitingForNetwork = shouldWait;

        if (!self.getWorld().isClientSide) {
            blockEntity.sendData();
        }
        if (notifyOutputs) {
            notifyRedstoneOutputs();
        }

        if (!satisfied && fluidlogistics$remainingAdditionalStock <= 0 && self.panelBE().restocker
                && fluidlogistics$hasAdditionalStock()) {
            fluidlogistics$remainingAdditionalStock = fluidlogistics$additionalStock;
        } else if (satisfied) {
            fluidlogistics$remainingAdditionalStock = 0;
        }
    }

    @Inject(
        method = "writeSafe",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$writeThresholdSafe(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo ci) {
        fluidlogistics$writeThreshold(nbt);
    }

    @Inject(
        method = "write",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$writeThreshold(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries,
            boolean clientPacket, CallbackInfo ci) {
        fluidlogistics$writeThreshold(nbt);
    }

    @Inject(
        method = "read",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$readThreshold(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries,
            boolean clientPacket, CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (!self.active) {
            return;
        }

        CompoundTag tag = nbt.getCompound(CreateLang.asId(self.slot.name()));
        if (tag.contains(fluidlogistics$RESTOCK_THRESHOLD_KEY, Tag.TAG_INT)) {
            fluidlogistics$setRestockThreshold(tag.getInt(fluidlogistics$RESTOCK_THRESHOLD_KEY));
        } else {
            fluidlogistics$setRestockThreshold(fluidlogistics$DEFAULT_RESTOCK_THRESHOLD);
        }

        if (tag.contains(fluidlogistics$PROMISE_LIMIT_KEY, Tag.TAG_INT)) {
            fluidlogistics$setPromiseLimit(tag.getInt(fluidlogistics$PROMISE_LIMIT_KEY));
        } else {
            fluidlogistics$setPromiseLimit(fluidlogistics$DEFAULT_PROMISE_LIMIT);
        }

        if (tag.contains(fluidlogistics$ADDITIONAL_STOCK_KEY, Tag.TAG_INT)) {
            fluidlogistics$setAdditionalStock(tag.getInt(fluidlogistics$ADDITIONAL_STOCK_KEY));
        } else {
            fluidlogistics$setAdditionalStock(fluidlogistics$DEFAULT_ADDITIONAL_STOCK);
        }

        fluidlogistics$remainingAdditionalStock = ResourceGaugeHelper
                .policy(self)
                .clampAdditionalStock(tag.getInt(fluidlogistics$REMAINING_ADDITIONAL_STOCK_KEY));
    }

    @Unique
    private void fluidlogistics$writeThreshold(CompoundTag nbt) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (!self.active) {
            return;
        }

        String tagName = CreateLang.asId(self.slot.name());
        CompoundTag tag = nbt.getCompound(tagName);
        tag.putInt(fluidlogistics$RESTOCK_THRESHOLD_KEY, fluidlogistics$restockThreshold);
        tag.putInt(fluidlogistics$PROMISE_LIMIT_KEY, fluidlogistics$promiseLimit);
        tag.putInt(fluidlogistics$ADDITIONAL_STOCK_KEY, fluidlogistics$additionalStock);
        tag.putInt(fluidlogistics$REMAINING_ADDITIONAL_STOCK_KEY, fluidlogistics$remainingAdditionalStock);
        nbt.put(tagName, tag);
    }

    @Unique
    private boolean fluidlogistics$shouldApplyResourceRestock() {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        return ResourceGaugeHelper.isResourceRestocker(self)
                && ResourceGaugeHelper.policy(self).hasConfigurableSettings();
    }
}

package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidAdditionalStock;
import com.yision.fluidlogistics.util.IFluidPromiseLimit;
import com.yision.fluidlogistics.util.IFluidRestockThreshold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelRestockThresholdMixin extends FilteringBehaviour
    implements IFluidRestockThreshold, IFluidPromiseLimit, IFluidAdditionalStock {

    @Shadow(remap = false)
    public boolean satisfied;

    @Shadow(remap = false)
    public boolean promisedSatisfied;

    @Shadow(remap = false)
    public boolean waitingForNetwork;

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
    private static final String fluidlogistics$RESTOCK_THRESHOLD_KEY = FluidGaugeHelper.RESTOCK_THRESHOLD_KEY;

    @Unique
    private static final String fluidlogistics$PROMISE_LIMIT_KEY = FluidGaugeHelper.PROMISE_LIMIT_KEY;

    @Unique
    private static final String fluidlogistics$ADDITIONAL_STOCK_KEY = FluidGaugeHelper.ADDITIONAL_STOCK_KEY;

    @Unique
    private static final String fluidlogistics$REMAINING_ADDITIONAL_STOCK_KEY =
        FluidGaugeHelper.REMAINING_ADDITIONAL_STOCK_KEY;

    @Unique
    private static final int fluidlogistics$DEFAULT_RESTOCK_THRESHOLD = FluidGaugeHelper.DEFAULT_RESTOCK_THRESHOLD;

    @Unique
    private static final int fluidlogistics$MAX_RESTOCK_THRESHOLD = FluidGaugeHelper.MAX_FLUID_AMOUNT;

    @Unique
    private static final int fluidlogistics$DEFAULT_PROMISE_LIMIT = FluidGaugeHelper.DEFAULT_PROMISE_LIMIT;

    @Unique
    private static final int fluidlogistics$MAX_PROMISE_LIMIT = FluidGaugeHelper.MAX_FLUID_AMOUNT;

    @Unique
    private static final int fluidlogistics$DEFAULT_ADDITIONAL_STOCK = FluidGaugeHelper.DEFAULT_ADDITIONAL_STOCK;

    @Unique
    private static final int fluidlogistics$MAX_ADDITIONAL_STOCK = FluidGaugeHelper.MAX_FLUID_AMOUNT;

    @Unique
    private int fluidlogistics$restockThreshold = fluidlogistics$DEFAULT_RESTOCK_THRESHOLD;

    @Unique
    private int fluidlogistics$promiseLimit = fluidlogistics$DEFAULT_PROMISE_LIMIT;

    @Unique
    private int fluidlogistics$additionalStock = fluidlogistics$DEFAULT_ADDITIONAL_STOCK;

    @Unique
    private int fluidlogistics$remainingAdditionalStock = fluidlogistics$DEFAULT_ADDITIONAL_STOCK;

    public FactoryPanelRestockThresholdMixin(SmartBlockEntity be, ValueBoxTransform slot) {
        super(be, slot);
    }

    @Override
    public int fluidlogistics$getRestockThreshold() {
        return fluidlogistics$restockThreshold;
    }

    @Override
    public void fluidlogistics$setRestockThreshold(int threshold) {
        fluidlogistics$restockThreshold = FluidGaugeHelper.clampRestockThreshold(threshold);
    }

    @Override
    public int fluidlogistics$getPromiseLimit() {
        return fluidlogistics$promiseLimit;
    }

    @Override
    public void fluidlogistics$setPromiseLimit(int limit) {
        fluidlogistics$promiseLimit = FluidGaugeHelper.clampPromiseLimit(limit);
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
        fluidlogistics$additionalStock = FluidGaugeHelper.clampAdditionalStock(amount);
        if (fluidlogistics$remainingAdditionalStock > fluidlogistics$additionalStock) {
            fluidlogistics$remainingAdditionalStock = fluidlogistics$additionalStock;
        }
        if (fluidlogistics$shouldApply() && !satisfied && fluidlogistics$remainingAdditionalStock <= 0
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
        cancellable = true
    )
    private void fluidlogistics$tickFluidRestockStorageMonitor(CallbackInfo ci) {
        if (!fluidlogistics$shouldApply()) {
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

        int threshold = FluidGaugeHelper.getEffectiveRestockThreshold(this);
        int promised = self.getPromised();
        int demand = self.getAmount() + fluidlogistics$remainingAdditionalStock;

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
        at = @At("RETURN")
    )
    private void fluidlogistics$writeThresholdSafe(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo ci) {
        fluidlogistics$writeThreshold(nbt);
    }

    @Inject(
        method = "write",
        at = @At("RETURN")
    )
    private void fluidlogistics$writeThreshold(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries,
            boolean clientPacket, CallbackInfo ci) {
        fluidlogistics$writeThreshold(nbt);
    }

    @Inject(
        method = "read",
        at = @At("RETURN")
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

        fluidlogistics$remainingAdditionalStock =
            FluidGaugeHelper.clampRemainingAdditionalStock(tag.getInt(fluidlogistics$REMAINING_ADDITIONAL_STOCK_KEY));
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
    private boolean fluidlogistics$shouldApply() {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        return FluidGaugeHelper.isVirtualFluidRestocker(self);
    }
}

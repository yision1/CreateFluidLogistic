package com.yision.fluidlogistics.content.fluids.faucet;

import com.yision.fluidlogistics.content.fluids.faucet.network.FaucetDripParticlePacket;
import com.yision.fluidlogistics.foundation.fluid.FluidSourceScans;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 龙头的滴液效果子系统：无目标时循环预览源流体并向附近玩家发滴液粒子。
 * 仅拥有滴液相关状态，其余（renderingFluid 清理、源查询、下方是否有目标）经 {@link FaucetBlockEntity} 回调。
 */
class FaucetDrips {

    private static final int DRIP_CACHE_REFRESH_INTERVAL = 120;
    private static final int DRIP_INTERVAL = 25;
    private static final int DRIP_AMOUNT = 250;
    private static final String TAG_DRIP_FLUID = "DripFluid";
    private static final String TAG_SHOULD_DRIP = "ShouldDrip";
    private static final String TAG_DRIP_TICK_COUNTER = "DripTickCounter";
    private static final String TAG_DRIP_CYCLE_INDEX = "DripCycleIndex";

    private final FaucetBlockEntity be;
    private FluidStack dripFluid = FluidStack.EMPTY;
    private final List<FluidStack> cachedDripFluids = new ArrayList<>();
    private int dripCacheRefreshCooldown;
    private int dripTickCounter;
    private int dripCycleIndex;
    private boolean shouldDrip;

    FaucetDrips(FaucetBlockEntity be) {
        this.be = be;
    }

    boolean isDripping() {
        return shouldDrip;
    }

    void tickCacheRefresh() {
        if (dripCacheRefreshCooldown > 0) {
            dripCacheRefreshCooldown--;
            return;
        }

        dripCacheRefreshCooldown = DRIP_CACHE_REFRESH_INTERVAL;
        if (shouldDrip || !cachedDripFluids.isEmpty()) {
            refreshCache();
        }
    }

    void tickEffect() {
        if (!shouldDrip) {
            return;
        }
        if (++dripTickCounter < DRIP_INTERVAL) {
            return;
        }
        dripTickCounter = 0;
        spawnParticle();
        advance();
    }

    void prime(IFluidHandler sourceHandler) {
        if (shouldDrip) {
            return;
        }

        if (!cachedDripFluids.isEmpty()) {
            apply(cachedDripFluids);
            return;
        }

        update(sourceHandler);
    }

    void update(IFluidHandler sourceHandler) {
        List<FluidStack> dripPreview = FluidSourceScans.previewDripFluids(sourceHandler, be::testFluidFilter, DRIP_AMOUNT, true);
        cache(dripPreview);
        apply(dripPreview);
    }

    private void refreshCache() {
        if (be.hasProcessableTargetBelow()) {
            return;
        }

        IFluidHandler sourceHandler = be.sourceHandler(be.sourcePos(), be.sourceFacing());
        if (sourceHandler == null) {
            cachedDripFluids.clear();
            be.clearFlowVisuals();
            return;
        }

        List<FluidStack> dripPreview = FluidSourceScans.previewDripFluids(sourceHandler, be::testFluidFilter, DRIP_AMOUNT, true);
        cache(dripPreview);
        apply(dripPreview);
    }

    private void apply(List<FluidStack> dripPreview) {
        if (dripPreview.isEmpty()) {
            be.clearFlowVisuals();
            return;
        }
        if (applyPreview(dripPreview) || !be.getRenderingFluid().isEmpty()) {
            be.setRenderingFluid(FluidStack.EMPTY);
            be.notifyUpdate();
        }
    }

    private boolean applyPreview(List<FluidStack> dripPreview) {
        boolean stateChanged = !shouldDrip;
        shouldDrip = true;
        if (!contains(dripPreview, dripFluid)) {
            int index = Math.floorMod(dripCycleIndex, dripPreview.size());
            FluidStack nextDripFluid = dripPreview.get(index).copyWithAmount(Math.min(dripPreview.get(index).getAmount(), DRIP_AMOUNT));
            boolean fluidChanged = !FluidStack.isSameFluidSameComponents(dripFluid, nextDripFluid);
            dripFluid = nextDripFluid;
            stateChanged |= fluidChanged;
            dripTickCounter = DRIP_INTERVAL - 1;
        }
        return stateChanged;
    }

    private void advance() {
        if (cachedDripFluids.isEmpty()) {
            clear();
            return;
        }

        int currentIndex = indexOf(cachedDripFluids, dripFluid);
        int nextIndex = currentIndex >= 0 ? (currentIndex + 1) % cachedDripFluids.size()
            : Math.floorMod(dripCycleIndex, cachedDripFluids.size());
        dripCycleIndex = nextIndex;
        FluidStack nextFluid = cachedDripFluids.get(nextIndex);
        dripFluid = nextFluid.copyWithAmount(Math.min(nextFluid.getAmount(), DRIP_AMOUNT));
    }

    void clear() {
        shouldDrip = false;
        dripTickCounter = 0;
        dripCycleIndex = 0;
        dripFluid = FluidStack.EMPTY;
    }

    void clearCache() {
        cachedDripFluids.clear();
    }

    private void cache(List<FluidStack> dripPreview) {
        cachedDripFluids.clear();
        for (FluidStack preview : dripPreview) {
            cachedDripFluids.add(preview.copyWithAmount(Math.min(preview.getAmount(), DRIP_AMOUNT)));
        }
    }

    private void spawnParticle() {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel serverLevel) || dripFluid.isEmpty()) {
            return;
        }

        Vec3 spoutPos = Vec3.atCenterOf(be.getBlockPos()).add(0, -0.3, 0);
        PacketDistributor.sendToPlayersNear(serverLevel, null, spoutPos.x, spoutPos.y, spoutPos.z, 32.0,
            new FaucetDripParticlePacket(be.getBlockPos(), dripFluid.copy()));

        if (level.random.nextFloat() < 0.2f) {
            level.playSound(null, be.getBlockPos(), net.minecraft.sounds.SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.2f, 0.8f + level.random.nextFloat() * 0.4f);
        }
    }

    private static boolean contains(List<FluidStack> fluids, FluidStack target) {
        return indexOf(fluids, target) != -1;
    }

    private static int indexOf(List<FluidStack> fluids, FluidStack target) {
        if (target.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < fluids.size(); index++) {
            if (FluidStack.isSameFluidSameComponents(fluids.get(index), target)) {
                return index;
            }
        }
        return -1;
    }

    void write(CompoundTag tag, HolderLookup.Provider registries) {
        FaucetFilling.writeFluid(tag, registries, TAG_DRIP_FLUID, dripFluid);
        tag.putBoolean(TAG_SHOULD_DRIP, shouldDrip);
        tag.putInt(TAG_DRIP_TICK_COUNTER, dripTickCounter);
        tag.putInt(TAG_DRIP_CYCLE_INDEX, dripCycleIndex);
    }

    void read(CompoundTag tag, HolderLookup.Provider registries) {
        dripFluid = FaucetFilling.readFluid(tag, registries, TAG_DRIP_FLUID);
        shouldDrip = tag.getBoolean(TAG_SHOULD_DRIP);
        dripTickCounter = tag.getInt(TAG_DRIP_TICK_COUNTER);
        dripCycleIndex = tag.getInt(TAG_DRIP_CYCLE_INDEX);
    }
}

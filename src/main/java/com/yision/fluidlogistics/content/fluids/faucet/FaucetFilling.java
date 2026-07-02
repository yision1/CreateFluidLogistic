package com.yision.fluidlogistics.content.fluids.faucet;

import com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.compat.createenchantmentindustry.CreateEnchantmentIndustryCompat;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public final class FaucetFilling {

    private FaucetFilling() {
    }

    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        if (FillingBySpout.canItemBeFilled(level, stack)) {
            return true;
        }

        return CompatMods.createEnchantmentIndustryLoaded()
            && CreateEnchantmentIndustryCompat.canRepairItem(stack);
    }

    public static int getRequiredAmountForItem(Level level, ItemStack stack, FluidStack availableFluid) {
        int requiredAmount = FillingBySpout.getRequiredAmountForItem(level, stack, availableFluid);
        if (requiredAmount != -1) {
            return requiredAmount;
        }

        if (CompatMods.createEnchantmentIndustryLoaded()) {
            int repairAmount = CreateEnchantmentIndustryCompat.getRequiredRepairFluidAmount(level, stack, availableFluid);
            if (repairAmount > 0) {
                return repairAmount;
            }
        }

        return -1;
    }

    public static ItemStack fillItem(Level level, int requiredAmount, ItemStack stack, FluidStack availableFluid) {
        int createRequired = FillingBySpout.getRequiredAmountForItem(level, stack, availableFluid.copy());
        if (createRequired != -1) {
            return FillingBySpout.fillItem(level, requiredAmount, stack, availableFluid);
        }

        if (CompatMods.createEnchantmentIndustryLoaded()) {
            return CreateEnchantmentIndustryCompat.repairItemWithFluid(level, requiredAmount, stack, availableFluid);
        }

        return ItemStack.EMPTY;
    }
}

final class FaucetFluidSupport {

    static final InfiniteWaterSourceHandler INFINITE_WATER_SOURCE = new InfiniteWaterSourceHandler();

    private FaucetFluidSupport() {
    }

    static @Nullable IFluidHandler getSourceHandler(Level level, BlockPos sourcePos, Direction side) {
        if (AbstractFaucetBlock.isInfiniteWaterSource(level.getBlockState(sourcePos))) {
            return INFINITE_WATER_SOURCE;
        }
        IFluidHandler sidedHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, side);
        if (sidedHandler != null) {
            return sidedHandler;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, null);
    }

    static boolean hasFluidCapability(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null;
    }

    static FluidStack previewFluid(IFluidHandler sourceHandler, Predicate<FluidStack> filter, int maxAmount) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }
            return candidate.copyWithAmount(Math.min(candidate.getAmount(), maxAmount));
        }

        FluidStack drained = sourceHandler.drain(maxAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && filter.test(drained)) {
            return drained;
        }
        return FluidStack.EMPTY;
    }

    static FluidStack findFillableFluidForItem(Level level, IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, ItemStack item) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }

            int requiredAmount = FaucetFilling.getRequiredAmountForItem(level, item, candidate.copy());
            if (requiredAmount > 0 && requiredAmount <= candidate.getAmount()) {
                return candidate.copy();
            }
        }

        FluidStack fallback = previewFluid(sourceHandler, filter, Integer.MAX_VALUE);
        if (fallback.isEmpty()) {
            return FluidStack.EMPTY;
        }

        int requiredAmount = FaucetFilling.getRequiredAmountForItem(level, item, fallback.copy());
        return requiredAmount > 0 && requiredAmount <= fallback.getAmount() ? fallback : FluidStack.EMPTY;
    }

    static boolean hasPotentialFluidForItem(Level level, IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, ItemStack item) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }

            if (FaucetFilling.getRequiredAmountForItem(level, item, candidate.copy()) > 0) {
                return true;
            }
        }

        FluidStack fallback = previewFluid(sourceHandler, filter, Integer.MAX_VALUE);
        return !fallback.isEmpty() && FaucetFilling.getRequiredAmountForItem(level, item, fallback.copy()) > 0;
    }

    static FluidStack findFillableFluidForCauldron(IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, BlockState targetState) {
        return findMatchingFluid(sourceHandler, filter,
            candidate -> canFillCauldron(targetState, candidate), Integer.MAX_VALUE);
    }

    static FluidStack findFillableFluidForContainer(IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, IFluidHandler targetHandler, int transferRate) {
        return findMatchingFluid(sourceHandler, filter, candidate -> {
            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), transferRate));
            return targetHandler.fill(preview, FluidAction.SIMULATE) > 0;
        }, transferRate);
    }

    static FluidStack findMatchingFluid(IFluidHandler sourceHandler, Predicate<FluidStack> filter,
        Predicate<FluidStack> predicate, int maxAmount) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }

            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), maxAmount));
            if (predicate.test(preview)) {
                return preview;
            }
        }

        FluidStack drained = sourceHandler.drain(maxAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && filter.test(drained) && predicate.test(drained)) {
            return drained;
        }
        return FluidStack.EMPTY;
    }

    static List<FluidStack> previewDripFluids(IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, int dripAmount) {
        List<FluidStack> dripFluids = new ArrayList<>();
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }

            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), dripAmount));
            boolean duplicate = false;
            for (FluidStack existing : dripFluids) {
                if (FluidStack.isSameFluidSameComponents(existing, preview)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                dripFluids.add(preview);
            }
        }

        if (!dripFluids.isEmpty()) {
            return dripFluids;
        }

        FluidStack drained = sourceHandler.drain(dripAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && filter.test(drained)) {
            dripFluids.add(drained.copyWithAmount(Math.min(drained.getAmount(), dripAmount)));
        }
        return dripFluids;
    }

    static boolean canFillCauldron(BlockState targetState, FluidStack availableFluid) {
        if (availableFluid.isEmpty()) {
            return false;
        }

        if (availableFluid.getFluid() == Fluids.WATER) {
            if (targetState.is(Blocks.CAULDRON)) {
                return availableFluid.getAmount() >= 250;
            }

            if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
                int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                return currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL && availableFluid.getAmount() >= 250;
            }
            return false;
        }

        if (!targetState.is(Blocks.CAULDRON)) {
            return false;
        }

        var cauldronInfo = CauldronSpoutingBehavior.CAULDRON_INFO.get(availableFluid.getFluid());
        return cauldronInfo != null && availableFluid.getAmount() >= cauldronInfo.amount();
    }

    static int saturatedAdd(int a, int b) {
        long sum = (long) a + (long) b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}

final class FaucetFluidDisplayHandler implements IFluidHandler {
    private final List<DisplayedFluid> fluids = new ArrayList<>();

    FaucetFluidDisplayHandler(IFluidHandler source, Predicate<FluidStack> filter) {
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack fluid = source.getFluidInTank(tank);
            if (fluid.isEmpty()) {
                continue;
            }
            if (!filter.test(fluid)) {
                continue;
            }
            mergeDisplayedFluid(fluid, source.getTankCapacity(tank));
        }
    }

    private void mergeDisplayedFluid(FluidStack additionalFluid, int additionalCapacity) {
        for (DisplayedFluid entry : fluids) {
            if (FluidStack.isSameFluidSameComponents(entry.fluid, additionalFluid)) {
                entry.merge(additionalFluid, additionalCapacity);
                return;
            }
        }
        fluids.add(new DisplayedFluid(additionalFluid, additionalCapacity));
    }

    @Override
    public int getTanks() {
        return fluids.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank).fluid.copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank).capacity : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}

final class DisplayedFluid {
    final FluidStack fluid;
    int capacity;

    DisplayedFluid(FluidStack fluid, int capacity) {
        this.fluid = fluid.copy();
        this.capacity = capacity;
    }

    void merge(FluidStack additionalFluid, int additionalCapacity) {
        fluid.setAmount(FaucetFluidSupport.saturatedAdd(fluid.getAmount(), additionalFluid.getAmount()));
        capacity = FaucetFluidSupport.saturatedAdd(capacity, additionalCapacity);
    }
}

final class FaucetTargetSupport {

    private static Field depotOutputBufferField;

    private FaucetTargetSupport() {
    }

    static FluidStack fillCauldron(Level level, IFluidHandler sourceHandler,
        BlockPos targetPos, BlockState targetState, FluidStack availableFluid) {
        if (availableFluid.getFluid() == Fluids.WATER) {
            if (targetState.is(Blocks.CAULDRON)) {
                return fillWaterCauldronLevel(level, sourceHandler, targetPos, 1);
            }

            if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
                int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                if (currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                    return fillWaterCauldronLevel(level, sourceHandler, targetPos, currentLevel + 1);
                }
            }
            return FluidStack.EMPTY;
        }

        if (!targetState.is(Blocks.CAULDRON)) {
            return FluidStack.EMPTY;
        }

        var cauldronInfo = CauldronSpoutingBehavior.CAULDRON_INFO.get(availableFluid.getFluid());
        if (cauldronInfo == null || availableFluid.getAmount() < cauldronInfo.amount()) {
            return FluidStack.EMPTY;
        }

        FluidStack drained = sourceHandler.drain(availableFluid.copyWithAmount(cauldronInfo.amount()), FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < cauldronInfo.amount()) {
            return FluidStack.EMPTY;
        }

        level.setBlockAndUpdate(targetPos, cauldronInfo.cauldron());
        level.playSound(null, targetPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
        return drained;
    }

    static FluidStack fillWaterCauldronLevel(Level level, IFluidHandler sourceHandler,
        BlockPos targetPos, int targetLevel) {
        FluidStack drained = sourceHandler.drain(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < 250) {
            return FluidStack.EMPTY;
        }

        level.setBlockAndUpdate(targetPos,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, targetLevel));
        level.playSound(null, targetPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.8f + targetLevel * 0.1f);
        return drained;
    }

    static FluidStack fillContainer(Level level, BlockPos faucetPos, IFluidHandler sourceHandler,
        IFluidHandler targetHandler, Predicate<FluidStack> filter, int transferRate) {
        FluidStack availableFluid = FaucetFluidSupport.findFillableFluidForContainer(sourceHandler, filter, targetHandler, transferRate);
        if (availableFluid.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidStack toTransfer = availableFluid.copyWithAmount(Math.min(availableFluid.getAmount(), transferRate));
        int filled = targetHandler.fill(toTransfer, FluidAction.SIMULATE);
        if (filled <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack actualDrain = sourceHandler.drain(toTransfer.copyWithAmount(filled), FluidAction.EXECUTE);
        if (actualDrain.isEmpty()) {
            return FluidStack.EMPTY;
        }

        targetHandler.fill(actualDrain, FluidAction.EXECUTE);
        if (level.random.nextFloat() < 0.1f) {
            com.simibubi.create.AllSoundEvents.SPOUTING.playOnServer(level, faucetPos, 0.3f, 0.9f + 0.2f * level.random.nextFloat());
        }
        return actualDrain;
    }

    static @Nullable IFluidHandler getTargetHandler(Level level, BlockEntity targetEntity) {
        IFluidHandler targetHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetEntity.getBlockPos(),
            Direction.UP);
        if (targetHandler == null) {
            targetHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetEntity.getBlockPos(), null);
        }
        return targetHandler;
    }

    static boolean isDepot(@Nullable BlockEntity entity) {
        return entity != null && DepotBehaviour.get(entity, DepotBehaviour.TYPE) != null;
    }

    static ItemStack getItemOnDepot(BlockEntity depot) {
        DepotBehaviour behaviour = DepotBehaviour.get(depot, DepotBehaviour.TYPE);
        return behaviour == null ? ItemStack.EMPTY : behaviour.getHeldItemStack();
    }

    static void storeDepotOutput(Level level, DepotBehaviour behaviour, ItemStack result, BlockPos targetPos) {
        try {
            if (depotOutputBufferField == null) {
                depotOutputBufferField = DepotBehaviour.class.getDeclaredField("processingOutputBuffer");
                depotOutputBufferField.setAccessible(true);
            }
            ItemStackHandler outputBuffer = (ItemStackHandler) depotOutputBufferField.get(behaviour);
            ItemStack remainder = result.copy();
            for (int slot = 0; slot < outputBuffer.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = outputBuffer.insertItem(slot, remainder, false);
            }
            if (!remainder.isEmpty()) {
                Containers.dropItemStack(level, targetPos.getX() + 0.5, targetPos.getY() + 0.75,
                    targetPos.getZ() + 0.5, remainder);
            }
        } catch (ReflectiveOperationException exception) {
            Containers.dropItemStack(level, targetPos.getX() + 0.5, targetPos.getY() + 0.75,
                targetPos.getZ() + 0.5, result);
        }
    }

    static void notifyTargetUpdate(Level level, BlockEntity targetEntity) {
        level.sendBlockUpdated(targetEntity.getBlockPos(), targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
        if (targetEntity instanceof SmartBlockEntity smartBlockEntity) {
            smartBlockEntity.notifyUpdate();
        }
    }
}

final class InfiniteWaterSourceHandler implements IFluidHandler {
    private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return WATER.copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
            return FluidStack.EMPTY;
        }
        return WATER.copyWithAmount(Math.min(resource.getAmount(), WATER.getAmount()));
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return maxDrain <= 0 ? FluidStack.EMPTY : WATER.copyWithAmount(Math.min(maxDrain, WATER.getAmount()));
    }
}

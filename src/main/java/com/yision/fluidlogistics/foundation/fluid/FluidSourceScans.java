package com.yision.fluidlogistics.foundation.fluid;

import com.yision.fluidlogistics.content.fluids.faucet.FaucetFilling;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class FluidSourceScans {

    private FluidSourceScans() {
    }

    public static FluidStack previewFluid(IFluidHandler sourceHandler, Predicate<FluidStack> filter, int maxAmount,
        boolean fallbackDrain) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }
            return candidate.copyWithAmount(Math.min(candidate.getAmount(), maxAmount));
        }

        if (!fallbackDrain) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = sourceHandler.drain(maxAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && filter.test(drained)) {
            return drained;
        }
        return FluidStack.EMPTY;
    }

    public static FluidStack findForItem(Level level, IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, ItemStack item, boolean fallbackDrain) {
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

        if (!fallbackDrain) {
            return FluidStack.EMPTY;
        }
        FluidStack fallback = previewFluid(sourceHandler, filter, Integer.MAX_VALUE, true);
        if (fallback.isEmpty()) {
            return FluidStack.EMPTY;
        }

        int requiredAmount = FaucetFilling.getRequiredAmountForItem(level, item, fallback.copy());
        return requiredAmount > 0 && requiredAmount <= fallback.getAmount() ? fallback : FluidStack.EMPTY;
    }

    public static boolean hasPotentialForItem(Level level, IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, ItemStack item, boolean fallbackDrain) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }

            if (FaucetFilling.getRequiredAmountForItem(level, item, candidate.copy()) > 0) {
                return true;
            }
        }

        if (!fallbackDrain) {
            return false;
        }
        FluidStack fallback = previewFluid(sourceHandler, filter, Integer.MAX_VALUE, true);
        return !fallback.isEmpty() && FaucetFilling.getRequiredAmountForItem(level, item, fallback.copy()) > 0;
    }

    public static FluidStack findForCauldron(IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, BlockState targetState, int maxAmount, boolean fallbackDrain) {
        return findMatchingFluid(sourceHandler, filter,
            candidate -> CauldronFills.canFill(targetState, candidate), maxAmount, fallbackDrain);
    }

    public static FluidStack findForContainer(IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, IFluidHandler targetHandler, int transferRate,
        Predicate<FluidStack> fillGate, boolean fallbackDrain) {
        return findMatchingFluid(sourceHandler, filter, candidate -> {
            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), transferRate));
            int accepted = targetHandler.fill(preview, FluidAction.SIMULATE);
            return accepted > 0 && fillGate.test(preview.copyWithAmount(Math.min(preview.getAmount(), accepted)));
        }, transferRate, fallbackDrain);
    }

    public static List<FluidStack> previewDripFluids(IFluidHandler sourceHandler,
        Predicate<FluidStack> filter, int dripAmount, boolean fallbackDrain) {
        List<FluidStack> dripFluids = new ArrayList<>();
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || !filter.test(candidate)) {
                continue;
            }

            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), dripAmount));
            if (!containsFluid(dripFluids, preview)) {
                dripFluids.add(preview);
            }
        }

        if (!dripFluids.isEmpty() || !fallbackDrain) {
            return dripFluids;
        }

        FluidStack drained = sourceHandler.drain(dripAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && filter.test(drained)) {
            dripFluids.add(drained.copyWithAmount(Math.min(drained.getAmount(), dripAmount)));
        }
        return dripFluids;
    }

    public static FluidStack findMatchingFluid(IFluidHandler sourceHandler, Predicate<FluidStack> filter,
        Predicate<FluidStack> predicate, int maxAmount, boolean fallbackDrain) {
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

        if (!fallbackDrain) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = sourceHandler.drain(maxAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && filter.test(drained) && predicate.test(drained)) {
            return drained;
        }
        return FluidStack.EMPTY;
    }

    private static boolean containsFluid(List<FluidStack> fluids, FluidStack target) {
        for (FluidStack fluid : fluids) {
            if (FluidStack.isSameFluidSameComponents(fluid, target)) {
                return true;
            }
        }
        return false;
    }
}

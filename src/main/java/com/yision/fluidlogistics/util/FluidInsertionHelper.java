package com.yision.fluidlogistics.util;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidInsertionHelper {

    private FluidInsertionHelper() {
    }

    private record TankSnapshot(FluidStack fluid, int capacity) {
    }

    public static boolean canAcceptAll(IFluidHandler fluidHandler, List<FluidStack> packageFluids) {
        if (packageFluids.isEmpty()) {
            return true;
        }

        if (fluidHandler instanceof SharedCapacityFluidHandler sharedCapacityFluidHandler) {
            return sharedCapacityFluidHandler.canFillAll(packageFluids);
        }

        List<TankSnapshot> tankSnapshots = new ArrayList<>();
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            tankSnapshots.add(new TankSnapshot(fluidHandler.getFluidInTank(tank).copy(), fluidHandler.getTankCapacity(tank)));
        }

        for (FluidStack fluid : packageFluids) {
            if (!reserveFluid(fluidHandler, tankSnapshots, fluid)) {
                return false;
            }
        }

        return true;
    }

    private static boolean reserveFluid(IFluidHandler fluidHandler, List<TankSnapshot> tankSnapshots, FluidStack fluid) {
        int remaining = fluid.getAmount();

        for (int tank = 0; tank < tankSnapshots.size() && remaining > 0; tank++) {
            TankSnapshot snapshot = tankSnapshots.get(tank);
            if (snapshot.fluid().isEmpty()) {
                continue;
            }
            if (!FluidStack.isSameFluidSameComponents(snapshot.fluid(), fluid)) {
                continue;
            }

            int space = snapshot.capacity() - snapshot.fluid().getAmount();
            if (space <= 0) {
                continue;
            }

            int inserted = Math.min(space, remaining);
            FluidStack updated = snapshot.fluid().copy();
            updated.grow(inserted);
            tankSnapshots.set(tank, new TankSnapshot(updated, snapshot.capacity()));
            remaining -= inserted;
        }

        for (int tank = 0; tank < tankSnapshots.size() && remaining > 0; tank++) {
            TankSnapshot snapshot = tankSnapshots.get(tank);
            if (!snapshot.fluid().isEmpty()) {
                continue;
            }
            if (!fluidHandler.isFluidValid(tank, fluid)) {
                continue;
            }

            int inserted = Math.min(snapshot.capacity(), remaining);
            FluidStack updated = fluid.copyWithAmount(inserted);
            tankSnapshots.set(tank, new TankSnapshot(updated, snapshot.capacity()));
            remaining -= inserted;
        }

        return remaining == 0;
    }
}

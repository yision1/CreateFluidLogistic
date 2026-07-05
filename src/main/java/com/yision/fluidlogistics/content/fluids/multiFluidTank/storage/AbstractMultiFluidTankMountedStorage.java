package com.yision.fluidlogistics.content.fluids.multiFluidTank.storage;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public abstract class AbstractMultiFluidTankMountedStorage<H extends IFluidHandler>
		extends WrapperMountedFluidStorage<H> implements SyncedMountedStorage {

	protected boolean dirty;
	protected final int capacity;
	protected final int tanks;

	protected AbstractMultiFluidTankMountedStorage(MountedFluidStorageType<?> type, H handler, int capacity,
			int tanks) {
		super(type, handler);
		this.capacity = capacity;
		this.tanks = tanks;
	}

	public List<FluidStack> getFluidsList() {
		List<FluidStack> fluids = new ArrayList<>();
		for (int i = 0; i < wrapped.getTanks(); i++) {
			fluids.add(wrapped.getFluidInTank(i).copy());
		}
		return fluids;
	}

	public int getCapacity() {
		return this.capacity;
	}

	public int getTanks() {
		return this.tanks;
	}

	@Override
	public boolean isDirty() {
		return this.dirty;
	}

	@Override
	public void markClean() {
		this.dirty = false;
	}

	protected static List<FluidStack> readLegacyFluids(HolderLookup.Provider registries, CompoundTag nbt, int tanks) {
		List<FluidStack> fluids = new ArrayList<>();
		CompoundTag fluidsNbt = nbt.getCompound("Fluids");
		for (int i = 0; i < tanks; i++) {
			if (fluidsNbt.contains(Integer.toString(i))) {
				FluidStack fluid = FluidStack.parseOptional(registries, fluidsNbt.getCompound(Integer.toString(i)));
				fluids.add(fluid);
			} else {
				fluids.add(FluidStack.EMPTY);
			}
		}
		return fluids;
	}

	protected static int readLegacyTanks(CompoundTag nbt) {
		return nbt.contains("Tanks") ? nbt.getInt("Tanks") : 8;
	}

	public static final class Handler implements IFluidHandler {
		private final List<FluidStack> tankFluids;
		private final int capacity;
		private final int tanks;
		public Runnable onChange = () -> {};

		public Handler(int capacity, int tanks, List<FluidStack> fluids) {
			this.capacity = capacity;
			this.tanks = tanks;
			this.tankFluids = new ArrayList<>();
			for (int i = 0; i < tanks; i++) {
				if (i < fluids.size()) {
					this.tankFluids.add(fluids.get(i).copy());
				} else {
					this.tankFluids.add(FluidStack.EMPTY);
				}
			}
		}

		@Override
		public int getTanks() {
			return this.tanks;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			if (tank >= 0 && tank < this.tanks) {
				return this.tankFluids.get(tank);
			}
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return this.capacity;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return true;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return 0;

			int totalFilled = 0;
			int remaining = resource.getAmount();

			int currentTotal = getTotalFluidAmount();
			int totalSpace = this.capacity - currentTotal;
			if (totalSpace <= 0)
				return 0;

			for (int i = 0; i < this.tanks; i++) {
				FluidStack existing = this.tankFluids.get(i);
				if (!existing.isEmpty() && FluidStack.isSameFluidSameComponents(existing, resource)) {
					int toFill = Math.min(remaining, totalSpace);
					if (action.execute()) {
						this.tankFluids.set(i, existing.copyWithAmount(existing.getAmount() + toFill));
						this.onChange.run();
					}
					totalFilled += toFill;
					remaining -= toFill;
					totalSpace -= toFill;
					if (remaining <= 0 || totalSpace <= 0)
						break;
				}
			}

			if (totalFilled == 0 && remaining > 0 && totalSpace > 0) {
				for (int i = 0; i < this.tanks; i++) {
					if (this.tankFluids.get(i).isEmpty()) {
						int toFill = Math.min(remaining, totalSpace);
						if (action.execute()) {
							this.tankFluids.set(i, resource.copyWithAmount(toFill));
							this.onChange.run();
						}
						totalFilled += toFill;
						break;
					}
				}
			}

			return totalFilled;
		}

		private int getTotalFluidAmount() {
			int total = 0;
			for (FluidStack fluid : this.tankFluids) {
				total += fluid.getAmount();
			}
			return total;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return FluidStack.EMPTY;
			for (int i = 0; i < this.tanks; i++) {
				FluidStack existing = this.tankFluids.get(i);
				if (!existing.isEmpty() && FluidStack.isSameFluidSameComponents(existing, resource)) {
					int drained = Math.min(existing.getAmount(), resource.getAmount());
					if (action.execute()) {
						this.tankFluids.set(i, existing.copyWithAmount(existing.getAmount() - drained));
						this.onChange.run();
					}
					return existing.copyWithAmount(drained);
				}
			}
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			for (int i = 0; i < this.tanks; i++) {
				FluidStack existing = this.tankFluids.get(i);
				if (!existing.isEmpty()) {
					int drained = Math.min(maxDrain, existing.getAmount());
					if (action.execute()) {
						this.tankFluids.set(i, existing.copyWithAmount(existing.getAmount() - drained));
						this.onChange.run();
					}
					return existing.copyWithAmount(drained);
				}
			}
			return FluidStack.EMPTY;
		}
	}
}

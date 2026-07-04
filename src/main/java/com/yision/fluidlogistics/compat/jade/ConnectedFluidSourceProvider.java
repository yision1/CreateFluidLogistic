package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetBlockEntity;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.JadeForgeUtils;

public enum ConnectedFluidSourceProvider
		implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
	INSTANCE;

	private static final ResourceLocation UID = FluidLogistics.asResource("connected_fluid_source");

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public int getDefaultPriority() {
		return 9998;
	}

	@Override
	public boolean shouldRequestData(Accessor<?> accessor) {
		return getDisplaySource(accessor) != null;
	}

	@Nullable
	@Override
	public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
		DisplaySource source = getDisplaySource(accessor);
		if (source == null) {
			return null;
		}
		return source.hideEmptyCapacity()
			? fromFluidHandlerWithoutEmptyCapacity(source.handler())
			: JadeForgeUtils.fromFluidHandler(source.handler());
	}

	@Override
	public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
		return ClientViewGroup.map(groups, FluidView::readDefault, null);
	}

	@Nullable
	private static DisplaySource getDisplaySource(Accessor<?> accessor) {
		if (!(accessor instanceof BlockAccessor blockAccessor)) {
			return null;
		}

		if (blockAccessor.getBlockEntity() instanceof AbstractFaucetBlockEntity faucet) {
			return withoutEmptyCapacity(faucet.getFluidDisplayCapability());
		}

		if (blockAccessor.getBlockEntity() instanceof FluidHatchBlockEntity hatch) {
			return withoutEmptyCapacity(hatch.getFluidDisplayCapability());
		}

		if (blockAccessor.getBlockEntity() instanceof MechanicalFluidGunBlockEntity gun) {
			return withoutEmptyCapacity(mergedNonEmpty(gun.sourceHandler()));
		}

		if (blockAccessor.getBlockEntity() instanceof MultiFluidAccessPortBlockEntity port) {
			IFluidHandler handler = port.getFluidDisplayCapability(blockAccessor.getSide());
			return handler == null ? null : new DisplaySource(handler, false);
		}

		return null;
	}

	@Nullable
	private static DisplaySource withoutEmptyCapacity(@Nullable IFluidHandler handler) {
		return handler == null ? null : new DisplaySource(handler, true);
	}

	private static List<ViewGroup<CompoundTag>> fromFluidHandlerWithoutEmptyCapacity(IFluidHandler handler) {
		List<CompoundTag> views = new ArrayList<>();
		for (int tank = 0; tank < handler.getTanks(); tank++) {
			FluidStack fluid = handler.getFluidInTank(tank);
			if (fluid.isEmpty()) {
				continue;
			}
			JadeFluidObject fluidObject = JadeFluidObject.of(
				fluid.getFluid(),
				fluid.getAmount(),
				fluid.getComponentsPatch()
			);
			views.add(FluidView.writeDefault(fluidObject, fluid.getAmount()));
		}
		return views.isEmpty() ? List.of() : List.of(new ViewGroup<>(views));
	}

	private record DisplaySource(IFluidHandler handler, boolean hideEmptyCapacity) {
	}

	@Nullable
	private static IFluidHandler mergedNonEmpty(@Nullable IFluidHandler source) {
		if (source == null) {
			return null;
		}
		MergedFluidDisplayHandler display = new MergedFluidDisplayHandler(source);
		return display.getTanks() == 0 ? null : display;
	}

	private static final class MergedFluidDisplayHandler implements IFluidHandler {
		private final List<DisplayedFluid> fluids = new ArrayList<>();

		MergedFluidDisplayHandler(IFluidHandler source) {
			for (int tank = 0; tank < source.getTanks(); tank++) {
				FluidStack fluid = source.getFluidInTank(tank);
				if (fluid.isEmpty()) {
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

		private static int saturatedAdd(int a, int b) {
			long sum = (long) a + b;
			return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
		}

		private static final class DisplayedFluid {
			final FluidStack fluid;
			int capacity;

			DisplayedFluid(FluidStack fluid, int capacity) {
				this.fluid = fluid.copy();
				this.capacity = capacity;
			}

			void merge(FluidStack additionalFluid, int additionalCapacity) {
				fluid.setAmount(saturatedAdd(fluid.getAmount(), additionalFluid.getAmount()));
				capacity = saturatedAdd(capacity, additionalCapacity);
			}
		}
	}
}

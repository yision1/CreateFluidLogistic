package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.JadeForgeUtils;

public enum MechanicalFluidGunFluidProvider
		implements IServerDataProvider<BlockAccessor>, IClientExtensionProvider<CompoundTag, FluidView> {
	INSTANCE;

	private static final ResourceLocation UID = FluidLogistics.asResource("mechanical_fluid_gun_fluids");

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public int getDefaultPriority() {
		return 9998;
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		if (!(accessor.getBlockEntity() instanceof MechanicalFluidGunBlockEntity gun)) {
			return;
		}

		IFluidHandler source = gun.sourceHandler();
		if (source == null) {
			return;
		}

		NonEmptyFluidDisplayHandler display = new NonEmptyFluidDisplayHandler(source);
		if (display.getTanks() == 0) {
			return;
		}

		List<ViewGroup<CompoundTag>> groups = JadeForgeUtils.fromFluidHandler(display);
		if (ViewGroup.saveList(data, "JadeFluidStorage", groups, Function.identity())) {
			data.putString("JadeFluidStorageUid", UID.toString());
		}
	}

	@Override
	public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
		return ClientViewGroup.map(groups, FluidView::readDefault, null);
	}

	private static class NonEmptyFluidDisplayHandler implements IFluidHandler {
		private final List<DisplayedFluid> fluids = new ArrayList<>();

		private NonEmptyFluidDisplayHandler(IFluidHandler source) {
			for (int tank = 0; tank < source.getTanks(); tank++) {
				FluidStack fluid = source.getFluidInTank(tank);
				if (!fluid.isEmpty()) {
					fluids.add(new DisplayedFluid(fluid.copy(), source.getTankCapacity(tank)));
				}
			}
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

	private record DisplayedFluid(FluidStack fluid, int capacity) {
	}
}

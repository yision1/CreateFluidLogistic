package com.yision.fluidlogistics.compat.jade;

import java.util.List;
import java.util.function.Function;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.capability.IFluidHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.JadeForgeUtils;

public enum MultiFluidAccessPortFluidProvider
		implements IServerDataProvider<BlockAccessor>, IClientExtensionProvider<CompoundTag, FluidView> {
	INSTANCE;

	private static final ResourceLocation UID = FluidLogistics.asResource("multi_fluid_access_port_fluids");

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
		if (!(accessor.getBlockEntity() instanceof MultiFluidAccessPortBlockEntity port)) {
			return;
		}

		IFluidHandler handler = port.getFluidDisplayCapability(accessor.getSide());
		if (handler == null) {
			return;
		}

		List<ViewGroup<CompoundTag>> groups = JadeForgeUtils.fromFluidHandler(handler);
		if (ViewGroup.saveList(data, "JadeFluidStorage", groups, Function.identity())) {
			data.putString("JadeFluidStorageUid", UID.toString());
		}
	}

	@Override
	public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
		return ClientViewGroup.map(groups, FluidView::readDefault, null);
	}
}

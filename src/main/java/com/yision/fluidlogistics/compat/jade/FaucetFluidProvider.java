package com.yision.fluidlogistics.compat.jade;

import java.util.List;
import java.util.function.Function;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.Faucet.AbstractFaucetBlockEntity;

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

public enum FaucetFluidProvider
		implements IServerDataProvider<BlockAccessor>, IClientExtensionProvider<CompoundTag, FluidView> {
	INSTANCE;

	private static final ResourceLocation UID = FluidLogistics.asResource("faucet_fluids");

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
		if (!(accessor.getBlockEntity() instanceof AbstractFaucetBlockEntity faucet)) {
			return;
		}

		IFluidHandler handler = faucet.getFluidDisplayCapability();
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

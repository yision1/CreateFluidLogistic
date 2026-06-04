package com.yision.fluidlogistics.compat.jade;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.JadeForgeUtils;

public enum MechanicalFluidGunFluidProvider
		implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
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
	public boolean shouldRequestData(Accessor<?> accessor) {
		return getSourceHandler(accessor) != null;
	}

	@Nullable
	@Override
	public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
		IFluidHandler source = getSourceHandler(accessor);
		return source == null ? null : JadeForgeUtils.fromFluidHandler(source);
	}

	@Override
	public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
		return ClientViewGroup.map(groups, FluidView::readDefault, null);
	}

	@Nullable
	private static IFluidHandler getSourceHandler(Accessor<?> accessor) {
		if (!(accessor instanceof BlockAccessor blockAccessor)) {
			return null;
		}
		if (!(blockAccessor.getBlockEntity() instanceof MechanicalFluidGunBlockEntity gun)) {
			return null;
		}
		return gun.sourceHandler();
	}
}

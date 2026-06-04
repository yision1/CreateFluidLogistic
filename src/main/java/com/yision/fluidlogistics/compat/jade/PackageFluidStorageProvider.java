package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum PackageFluidStorageProvider implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
	INSTANCE;

	private static final ResourceLocation UID = FluidLogistics.asResource("package_fluid_storage");

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public @Nullable List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
		if (!(accessor instanceof EntityAccessor entityAccessor)) {
			return null;
		}

		if (!(entityAccessor.getEntity() instanceof PackageEntity packageEntity)) {
			return null;
		}

		ItemStack box = packageEntity.getBox();
		if (box.isEmpty() || !PackageItem.isPackage(box)) {
			return null;
		}

		ItemStackHandler contents = PackageItem.getContents(box);
		List<FluidStack> fluids = new ArrayList<>();
		int capacity = Config.getFluidPerPackage();

		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack slotStack = contents.getStackInSlot(i);
			if (!slotStack.isEmpty() && slotStack.getItem() instanceof CompressedTankItem) {
				FluidStack fluid = CompressedTankItem.getFluid(slotStack);
				int totalAmount = fluid.getAmount() * slotStack.getCount();
				mergeFluid(fluids, fluid, totalAmount);
			}
		}

		if (fluids.isEmpty()) {
			return List.of();
		}

		List<CompoundTag> views = new ArrayList<>();
		for (FluidStack fluid : fluids) {
			JadeFluidObject fluidObject = JadeFluidObject.of(
				fluid.getFluid(),
				fluid.getAmount(),
				fluid.getComponentsPatch()
			);
			views.add(FluidView.writeDefault(fluidObject, capacity));
		}

		return List.of(new ViewGroup<>(views));
	}

	private void mergeFluid(List<FluidStack> fluids, FluidStack newFluid, int totalAmount) {
		for (FluidStack existing : fluids) {
			if (FluidStack.isSameFluidSameComponents(existing, newFluid)) {
				existing.grow(totalAmount);
				return;
			}
		}
		FluidStack copy = newFluid.copy();
		copy.setAmount(totalAmount);
		fluids.add(copy);
	}

	@Override
	public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
		return ClientViewGroup.map(groups, FluidView::readDefault, null);
	}

	@Override
	public boolean shouldRequestData(Accessor<?> accessor) {
		return accessor instanceof EntityAccessor entityAccessor &&
			entityAccessor.getEntity() instanceof PackageEntity;
	}

	@Override
	public int getDefaultPriority() {
		return -5000;
	}
}

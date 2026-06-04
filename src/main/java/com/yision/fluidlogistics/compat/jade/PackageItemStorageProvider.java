package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.items.ItemStackHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

public enum PackageItemStorageProvider implements IServerExtensionProvider<ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {
	INSTANCE;

	private static final ResourceLocation UID = FluidLogistics.asResource("package_item_storage");

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
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
		List<ItemStack> displayItems = new ArrayList<>();

		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack slotStack = contents.getStackInSlot(i);
			if (slotStack.isEmpty()) continue;

			if (slotStack.getItem() instanceof CompressedTankItem) {
				continue;
			}
			displayItems.add(slotStack);
		}

		if (displayItems.isEmpty()) {
			return List.of();
		}

		return List.of(new ViewGroup<>(displayItems));
	}

	@Override
	public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
		return ClientViewGroup.map(groups, ItemView::new, null);
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

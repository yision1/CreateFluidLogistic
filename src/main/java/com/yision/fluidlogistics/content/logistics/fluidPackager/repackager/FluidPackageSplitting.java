package com.yision.fluidlogistics.content.logistics.fluidPackager.repackager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageItem.PackageOrderData;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.registry.AllItems;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class FluidPackageSplitting {

	private FluidPackageSplitting() {
	}

	public static List<ItemStack> split(ItemStack box) {
		List<ItemStack> result = new ArrayList<>();
		if (!PackageItem.isPackage(box)) {
			return result;
		}

		ItemStackHandler contents = readRawContents(box);

		LinkedHashMap<FluidTypeKey, FluidStack> fluidsByType = new LinkedHashMap<>();
		List<ItemStack> nonFluidItems = new ArrayList<>();

		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack stack = contents.getStackInSlot(i);
			if (stack.isEmpty())
				continue;

			if (stack.getItem() instanceof CompressedTankItem) {
				FluidStack fluid = CompressedTankItem.getFluid(stack);
				if (fluid.isEmpty())
					continue;
				int totalAmount = fluid.getAmount() * stack.getCount();
				FluidTypeKey key = FluidTypeKey.of(fluid);
				fluidsByType.compute(key, (k, existing) -> {
					if (existing == null)
						return fluid.copyWithAmount(totalAmount);
					existing.grow(totalAmount);
					return existing;
				});
			} else {
				nonFluidItems.add(stack.copy());
			}
		}

		String address = PackageItem.getAddress(box);
		PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
		PackageOrderData orderData = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
		boolean hasFluids = !fluidsByType.isEmpty();
		boolean hasItems = !nonFluidItems.isEmpty();
		boolean unchanged = !hasFluids || (!hasItems && isFluidPackage(box));

		if (unchanged) {
			result.add(box.copyWithCount(1));
			return result;
		}

		for (FluidStack fluid : fluidsByType.values())
			result.addAll(createFluidPackages(fluid, fluid.getAmount()));

		if (!nonFluidItems.isEmpty())
			result.addAll(createItemPackages(nonFluidItems));

		applyPackageMetadata(result, address, orderData, orderContext);
		return result;
	}

	public static boolean isFluidPackage(ItemStack stack) {
		return stack.getItem() instanceof FluidPackageItem;
	}

	public static ItemStackHandler readRawContents(ItemStack box) {
		ItemStackHandler newInv = new ItemStackHandler(PackageItem.SLOTS);
		ItemContainerContents contents =
			box.getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY);
		ItemHelper.fillItemStackHandler(contents, newInv);
		return newInv;
	}

	public static FluidStack collectFluid(ItemStackHandler contents) {
		FluidStack result = FluidStack.EMPTY;
		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack stack = contents.getStackInSlot(i);
			if (stack.isEmpty())
				continue;
			FluidStack fluid = CompressedTankItem.getFluid(stack);
			if (fluid.isEmpty())
				continue;
			if (result.isEmpty()) {
				result = fluid.copy();
			} else {
				result.grow(fluid.getAmount());
			}
		}
		return result;
	}

	private static List<ItemStack> createFluidPackages(FluidStack fluidType, int totalAmount) {
		List<ItemStack> packages = new ArrayList<>();
		int tankCapacity = CompressedTankItem.getCapacity();
		int maxTanks = PackageItem.SLOTS;
		int maxFluidPerPackage = tankCapacity * maxTanks;

		int remaining = totalAmount;

		while (remaining > 0) {
			int fluidForPackage = Math.min(remaining, maxFluidPerPackage);
			ItemStackHandler packageContents = new ItemStackHandler(PackageItem.SLOTS);
			int fluidRemaining = fluidForPackage;
			int tanksCreated = 0;

			while (fluidRemaining > 0 && tanksCreated < maxTanks) {
				int fluidForTank = Math.min(fluidRemaining, tankCapacity);
				ItemStack compressedTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
				CompressedTankItem.setFluid(compressedTank, fluidType.copyWithAmount(fluidForTank));
				packageContents.setStackInSlot(tanksCreated, compressedTank);
				fluidRemaining -= fluidForTank;
				tanksCreated++;
			}

			ItemStack fluidPackage = AllItems.createFluidPackage();
			fluidPackage.set(AllDataComponents.PACKAGE_CONTENTS,
				ItemHelper.containerContentsFromHandler(packageContents));
			packages.add(fluidPackage);

			remaining -= fluidForPackage;
		}

		return packages;
	}

	private static List<ItemStack> createItemPackages(List<ItemStack> items) {
		List<ItemStack> packages = new ArrayList<>();
		List<ItemStack> outputSlots = new ArrayList<>();

		for (ItemStack item : items) {
			int remaining = item.getCount();
			int maxStack = item.getMaxStackSize();
			while (remaining > 0) {
				int amount = Math.min(remaining, maxStack);
				outputSlots.add(item.copyWithCount(amount));
				remaining -= amount;
			}
		}

		int currentSlot = 0;
		ItemStackHandler target = new ItemStackHandler(PackageItem.SLOTS);

		for (ItemStack slotStack : outputSlots) {
			target.setStackInSlot(currentSlot++, slotStack);
			if (currentSlot < PackageItem.SLOTS)
				continue;
			packages.add(PackageItem.containing(target));
			target = new ItemStackHandler(PackageItem.SLOTS);
			currentSlot = 0;
		}

		for (int i = 0; i < target.getSlots(); i++) {
			if (!target.getStackInSlot(i).isEmpty()) {
				packages.add(PackageItem.containing(target));
				break;
			}
		}

		return packages;
	}

	private static void applyPackageMetadata(List<ItemStack> packages, String address, PackageOrderData orderData,
											 PackageOrderWithCrafts orderContext) {
		int resetOrderId = ThreadLocalRandom.current().nextInt();
		PackageOrderWithCrafts context = orderContext != null ? orderContext : orderData != null ? orderData.orderContext() : null;

		for (int i = 0; i < packages.size(); i++) {
			ItemStack output = packages.get(i);
			PackageItem.clearAddress(output);
			PackageItem.addAddress(output, address);

			PackageOrderWithCrafts fragmentContext = i == packages.size() - 1 ? context : null;
			PackageItem.setOrder(output, resetOrderId, 0, true, i, i == packages.size() - 1, fragmentContext);
		}
	}

	private record FluidTypeKey(FluidStack template) {
		static FluidTypeKey of(FluidStack stack) {
			return new FluidTypeKey(stack.copyWithAmount(1));
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof FluidTypeKey other
				&& FluidStack.isSameFluidSameComponents(template, other.template);
		}

		@Override
		public int hashCode() {
			return Objects.hash(template.getFluid(), template.getComponentsPatch());
		}
	}

}

package com.yision.fluidlogistics.content.logistics.fluidPackager.repackager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackager.PackagerGoggleInfo;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FluidRepackagerBlockEntity extends PackagerBlockEntity
	implements Clearable, IPackagerOverrideData, IHaveGoggleInformation {

	private final List<ItemStack> stalledPackages;
	private boolean manualOverrideLocked;
	private int syncedQueuedPackageCount;
	private final IItemHandler externalItemHandler;

	public FluidRepackagerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		stalledPackages = new ArrayList<>();
		externalItemHandler = new FluidRepackagerItemHandler();
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
			Capabilities.ItemHandler.BLOCK,
			AllBlockEntities.FLUID_REPACKAGER.get(),
			(be, context) -> {
				return be.externalItemHandler;
			}
		);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

		int cachedPackageCount = getCachedPackageCount();
		if (!manualOverrideLocked && cachedPackageCount <= 0) {
			return false;
		}

		PackagerGoggleInfo.addFluidRepackagerToTooltip(tooltip, "", manualOverrideLocked, cachedPackageCount);
		return true;
	}

	private int getCachedPackageCount() {
		int count = stalledPackages.size();
		if (level != null && level.isClientSide) {
			return count + syncedQueuedPackageCount;
		}
		for (BigItemStack entry : queuedExitingPackages) {
			count += Math.max(0, entry.count);
		}
		return count;
	}

	@Override
	public void tick() {

		super.tick();

		if (!level.isClientSide() && animationTicks == 0 && !stalledPackages.isEmpty()) {
			tryDeliverStalledPackages();
		}
	}

	@Override
	public boolean unwrapBox(ItemStack box, boolean simulate) {

		if (animationTicks > 0 || !stalledPackages.isEmpty()) {
			return false;
		}

		Objects.requireNonNull(this.level);

		Direction facing = getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
		BlockPos targetPos = worldPosition.relative(facing.getOpposite());
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBE = level.getBlockEntity(targetPos);

		if (!isSolidLiquidMixedTarget(targetPos, targetState, targetBE, facing)) {
			return insertPackageIntoTargetInventory(box, simulate);
		}

		List<ItemStack> splitResults = FluidPackageSplitting.split(box);
		if (splitResults.isEmpty()) {
			return false;
		}

		if (simulate) {
			return true;
		}

		computerBehaviour.prepareComputerEvent(new PackageEvent(box, "package_received"));
		previouslyUnwrapped = box.copyWithCount(1);
		animationInward = true;
		animationTicks = CYCLE;

		deliverToTarget(splitResults, targetPos, targetState, targetBE, facing);

		notifyUpdate();
		return true;
	}

	private boolean insertPackageIntoTargetInventory(ItemStack box, boolean simulate) {
		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler) {
			return false;
		}

		ItemStack boxToInsert = box.copyWithCount(1);
		boolean targetIsCreativeCrate = targetInv instanceof BottomlessItemHandler;
		boolean anySpace = false;

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack remainder = targetInv.insertItem(slot, boxToInsert, simulate);
			if (!remainder.isEmpty()) {
				continue;
			}
			anySpace = true;
			break;
		}

		if (!targetIsCreativeCrate && !anySpace) {
			return false;
		}
		if (simulate) {
			return true;
		}

		computerBehaviour.prepareComputerEvent(new PackageEvent(box, "package_received"));
		previouslyUnwrapped = boxToInsert;
		animationInward = true;
		animationTicks = CYCLE;
		notifyUpdate();
		return true;
	}

	@Override
	public void attemptToSend(List<PackagingRequest> queuedRequests) {

		if (queuedRequests != null) {
			return;
		}

		if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0 || !stalledPackages.isEmpty()) {
			return;
		}
		if (!queuedExitingPackages.isEmpty()) {
			return;
		}

		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler) {
			return;
		}

		Direction facing = getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
		BlockPos targetPos = worldPosition.relative(facing.getOpposite());
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBE = level.getBlockEntity(targetPos);

		boolean mixedTarget = isSolidLiquidMixedTarget(targetPos, targetState, targetBE, facing);

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack extracted = targetInv.extractItem(slot, 1, true);
			if (extracted.isEmpty() || !PackageItem.isPackage(extracted)) {
				continue;
			}

			List<ItemStack> splitResults = FluidPackageSplitting.split(extracted);
			if (splitResults.isEmpty()) {
				continue;
			}

			targetInv.extractItem(slot, 1, false);

			computerBehaviour.prepareComputerEvent(new PackageEvent(extracted, "package_received"));

			if (mixedTarget) {
				previouslyUnwrapped = extracted.copyWithCount(1);
				animationInward = true;
				animationTicks = CYCLE;
				deliverToTarget(splitResults, targetPos, targetState, targetBE, facing);
			} else {
				queueSplitResults(splitResults);
			}

			notifyUpdate();
			return;
		}
	}

	@Override
	public ItemStack getRenderedBox() {
		if (animationTicks == 0 && heldBox.isEmpty() && !stalledPackages.isEmpty()) {
			return stalledPackages.get(0);
		}
		return super.getRenderedBox();
	}

	public boolean hasStalledPackageReady() {
		return animationTicks == 0 && heldBox.isEmpty() && !stalledPackages.isEmpty();
	}

	public ItemStack extractStalledPackage(boolean simulate) {
		if (!hasStalledPackageReady()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = stalledPackages.get(0).copy();
		if (!simulate) {
			stalledPackages.remove(0);
			setChanged();
			notifyUpdate();
		}
		return stack;
	}

	private void tryDeliverStalledPackages() {
		Direction facing = getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
		BlockPos targetPos = worldPosition.relative(facing.getOpposite());
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBE = level.getBlockEntity(targetPos);

		if (!isSolidLiquidMixedTarget(targetPos, targetState, targetBE, facing)) {
			return;
		}

		Iterator<ItemStack> it = stalledPackages.iterator();
		while (it.hasNext()) {
			ItemStack pkg = it.next();
			if (simulateSinglePackage(pkg, targetPos, targetState, targetBE, facing)
				&& executeSinglePackage(pkg, targetPos, targetState, targetBE, facing)) {
				previouslyUnwrapped = pkg.copyWithCount(1);
				animationInward = true;
				animationTicks = CYCLE;
				it.remove();
				setChanged();
				notifyUpdate();
				return;
			}
		}
	}

	private void deliverToTarget(List<ItemStack> packages, BlockPos targetPos, BlockState targetState,
								 BlockEntity targetBE, Direction facing) {
		boolean stalledAny = false;
		for (ItemStack pkg : packages) {
			if (simulateSinglePackage(pkg, targetPos, targetState, targetBE, facing)
				&& executeSinglePackage(pkg, targetPos, targetState, targetBE, facing)) {
				continue;
			}

			stalledPackages.add(pkg.copy());
			stalledAny = true;
		}

		if (stalledAny)
			setChanged();
	}

	private void queueSplitResults(List<ItemStack> packages) {
		for (ItemStack pkg : packages) {
			queuedExitingPackages.add(new BigItemStack(pkg.copyWithCount(1), pkg.getCount()));
		}
		setChanged();
	}

	private boolean simulateSinglePackage(ItemStack pkg, BlockPos targetPos, BlockState targetState,
										  BlockEntity targetBE, Direction facing) {
		if (FluidPackageSplitting.isFluidPackage(pkg)) {
			return unpackFluidToTarget(pkg, targetPos, targetState, targetBE, facing, true);
		}
		return unpackItemsToTarget(pkg, targetPos, targetState, targetBE, facing, true);
	}

	private boolean executeSinglePackage(ItemStack pkg, BlockPos targetPos, BlockState targetState,
										 BlockEntity targetBE, Direction facing) {
		if (FluidPackageSplitting.isFluidPackage(pkg)) {
			return unpackFluidToTarget(pkg, targetPos, targetState, targetBE, facing, false);
		}
		return unpackItemsToTarget(pkg, targetPos, targetState, targetBE, facing, false);
	}

	private boolean unpackFluidToTarget(ItemStack fluidPackage, BlockPos targetPos, BlockState targetState,
										@Nullable BlockEntity targetBE, Direction facing, boolean simulate) {
		IFluidHandler fluidHandler = level.getCapability(
			Capabilities.FluidHandler.BLOCK, targetPos, targetState, targetBE, facing);
		if (fluidHandler == null) {
			return false;
		}

		ItemStackHandler contents = FluidPackageSplitting.readRawContents(fluidPackage);
		FluidStack fluid = FluidPackageSplitting.collectFluid(contents);
		if (fluid.isEmpty()) {
			return true;
		}

		int accepted = fluidHandler.fill(fluid.copy(),
			simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
		return accepted == fluid.getAmount();
	}

	private boolean unpackItemsToTarget(ItemStack itemPackage, BlockPos targetPos, BlockState targetState,
										@Nullable BlockEntity targetBE, Direction facing, boolean simulate) {
		ItemStackHandler contents = FluidPackageSplitting.readRawContents(itemPackage);
		List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents);
		if (items.isEmpty()) {
			return true;
		}

		PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(itemPackage);
		UnpackingHandler handler = UnpackingHandler.REGISTRY.get(targetState);
		UnpackingHandler toUse = handler != null ? handler : UnpackingHandler.DEFAULT;
		return toUse.unpack(level, targetPos, targetState, facing, items, orderContext, simulate);
	}

	private boolean isSolidLiquidMixedTarget(BlockPos targetPos, BlockState targetState,
											 @Nullable BlockEntity targetBE, Direction facing) {
		boolean hasItemHandler = UnpackingHandler.REGISTRY.get(targetState) != null
			|| level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState, targetBE, facing) != null;
		boolean hasFluidHandler = level.getCapability(
			Capabilities.FluidHandler.BLOCK, targetPos, targetState, targetBE, facing) != null;
		return hasItemHandler && hasFluidHandler;
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		manualOverrideLocked = compound.getBoolean("FluidLogisticsManualOverrideLocked");
		syncedQueuedPackageCount = compound.getInt("FluidLogisticsQueuedPackageCount");

		stalledPackages.clear();
		ListTag list = compound.getList("StalledPackages", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
			if (!stack.isEmpty()) {
				stalledPackages.add(stack);
			}
		}
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.putBoolean("FluidLogisticsManualOverrideLocked", manualOverrideLocked);
		compound.putInt("FluidLogisticsQueuedPackageCount", countQueuedPackages());

		ListTag list = new ListTag();
		for (ItemStack stack : stalledPackages) {
			list.add(stack.saveOptional(registries));
		}
		compound.put("StalledPackages", list);
	}

	private int countQueuedPackages() {
		int count = 0;
		for (BigItemStack entry : queuedExitingPackages) {
			count += Math.max(0, entry.count);
		}
		return count;
	}

	@Override
	public void clearContent() {
		super.clearContent();
		stalledPackages.clear();
	}

	@Override
	public void destroy() {
		super.destroy();
		for (ItemStack stack : stalledPackages) {
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
				stack.copy());
		}
		stalledPackages.clear();
	}

	@Override
	public boolean fluidlogistics$isManualOverrideLocked() {
		return manualOverrideLocked;
	}

	@Override
	public void fluidlogistics$setManualOverrideLocked(boolean locked) {
		manualOverrideLocked = locked;
	}

	@Override
	public String fluidlogistics$getClipboardAddress() {
		return "";
	}

	@Override
	public void fluidlogistics$setClipboardAddress(String address) {
	}

	private class FluidRepackagerItemHandler implements IItemHandler {

		@Override
		public int getSlots() {
			return inventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			if (!heldBox.isEmpty()) {
				return inventory.getStackInSlot(slot);
			}
			if (slot == 0 && hasStalledPackageReady()) {
				return stalledPackages.get(0);
			}
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return inventory.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (slot != 0 || amount <= 0) {
				return ItemStack.EMPTY;
			}
			if (!heldBox.isEmpty()) {
				return inventory.extractItem(slot, amount, simulate);
			}
			return extractStalledPackage(simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return inventory.isItemValid(slot, stack);
		}
	}
}

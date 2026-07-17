package com.yision.fluidlogistics.content.logistics.fluidPackager.repackager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackager.PackagerGoggleInfo;
import com.yision.fluidlogistics.api.packager.PackageInspection;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageUnpackContext;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageContentHelper;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FluidRepackagerBlockEntity extends PackagerBlockEntity
	implements Clearable, IHaveGoggleInformation {

	private final List<ItemStack> stalledPackages;
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
		IPackagerOverrideData overrideData = fluidlogistics$overrideData();
		int cachedPackageCount = getCachedPackageCount();
		if (!overrideData.fluidlogistics$isManualOverrideLocked() && cachedPackageCount <= 0) {
			return false;
		}

		PackagerGoggleInfo.addFluidRepackagerToTooltip(
			tooltip, "", overrideData.fluidlogistics$isManualOverrideLocked(), cachedPackageCount);
		return true;
	}

	private int getCachedPackageCount() {
		return stalledPackages.size() + fluidlogistics$overrideData().fluidlogistics$getQueuedPackageCount();
	}

	private IPackagerOverrideData fluidlogistics$overrideData() {
		return (IPackagerOverrideData) (Object) this;
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

		List<ItemStack> splitResults = PackageResources.splitPackage(box);
		if (splitResults.isEmpty()) {
			return false;
		}
		if (!canDeliverSplitResults(splitResults, targetPos, targetState, targetBE, facing)) {
			return insertPackageIntoTargetInventory(box, simulate);
		}

		if (simulate) {
			return true;
		}

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

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack extracted = targetInv.extractItem(slot, 1, true);
			if (extracted.isEmpty() || !PackageItem.isPackage(extracted)) {
				continue;
			}

			List<ItemStack> splitResults = PackageResources.splitPackage(extracted);
			if (splitResults.isEmpty()) {
				continue;
			}

			targetInv.extractItem(slot, 1, false);

			if (canDeliverSplitResults(splitResults, targetPos, targetState, targetBE, facing)) {
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

		if (!canDeliverSplitResults(stalledPackages, targetPos, targetState, targetBE, facing)) {
			return;
		}

		Iterator<ItemStack> it = stalledPackages.iterator();
		while (it.hasNext()) {
			ItemStack pkg = it.next();
			if (deliverSinglePackage(pkg, targetPos, targetState, targetBE, facing)) {
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
			if (deliverSinglePackage(pkg, targetPos, targetState, targetBE, facing)) {
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

	private boolean deliverSinglePackage(ItemStack pkg, BlockPos targetPos, BlockState targetState,
										BlockEntity targetBE, Direction facing) {
		PackageInspection inspection = PackageResources.inspectPackage(pkg);
		if (inspection.hasResources()) {
			return unpackResourceToTarget(pkg, targetPos, targetState, targetBE, facing, false);
		}
		return unpackItemsToTarget(pkg, targetPos, targetState, targetBE, facing, true)
			&& unpackItemsToTarget(pkg, targetPos, targetState, targetBE, facing, false);
	}

	private boolean unpackResourceToTarget(ItemStack resourcePackage, BlockPos targetPos, BlockState targetState,
										@Nullable BlockEntity targetBE, Direction facing, boolean simulate) {
		PackageUnpackContext context = new PackageUnpackContext(
			level, targetPos, targetState, targetBE, facing, resourcePackage,
			PackageItem.getOrderContext(resourcePackage));
		return PackageResources.unpackPackage(context, simulate);
	}

	private boolean unpackItemsToTarget(ItemStack itemPackage, BlockPos targetPos, BlockState targetState,
										@Nullable BlockEntity targetBE, Direction facing, boolean simulate) {
		ItemStackHandler contents = FluidPackageContentHelper.readRawContents(itemPackage);
		List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents);
		if (items.isEmpty()) {
			return true;
		}

		PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(itemPackage);
		UnpackingHandler handler = UnpackingHandler.REGISTRY.get(targetState);
		UnpackingHandler toUse = handler != null ? handler : UnpackingHandler.DEFAULT;
		return toUse.unpack(level, targetPos, targetState, facing, items, orderContext, simulate);
	}

	private boolean canDeliverSplitResults(List<ItemStack> packages, BlockPos targetPos, BlockState targetState,
											 @Nullable BlockEntity targetBE, Direction facing) {
		boolean hasResources = false;
		boolean resourceAccepted = false;
		boolean hasOrdinaryItems = false;
		for (ItemStack pkg : packages) {
			PackageInspection inspection = PackageResources.inspectPackage(pkg);
			if (inspection.hasResources()) {
				hasResources = true;
				resourceAccepted |= unpackResourceToTarget(pkg, targetPos, targetState, targetBE, facing, true);
			} else {
				hasOrdinaryItems = true;
			}
		}
		if (!hasResources || !resourceAccepted) {
			return false;
		}
		if (!hasOrdinaryItems) {
			return true;
		}
		return UnpackingHandler.REGISTRY.get(targetState) != null
			|| level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState, targetBE, facing) != null;
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);

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

		ListTag list = new ListTag();
		for (ItemStack stack : stalledPackages) {
			list.add(stack.saveOptional(registries));
		}
		compound.put("StalledPackages", list);
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

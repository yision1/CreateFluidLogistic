package com.yision.fluidlogistics.block.SmartHopper;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

public class SmartHopperBlockEntity extends SmartBlockEntity {

	private static final int ITEM_SLOTS = 5;
	private static final int FLUID_CAPACITY = 1000;
	private static final int ITEM_TRANSFER_AMOUNT = 1;
	private static final int FLUID_TRANSFER_AMOUNT = 250;
	private static final int TRANSFER_COOLDOWN_TICKS = 8;
	private static final int FAILED_TRANSFER_COOLDOWN_TICKS = 4;
	private static final String INVENTORY_NBT_KEY = "Inventory";

	private final ItemStackHandler inventory = new ItemStackHandler(ITEM_SLOTS) {
		@Override
		protected void onContentsChanged(int slot) {
			notifyUpdate();
		}
	};

	private final IItemHandler exposedItemHandler = new SmartHopperItemHandler();
	private final IFluidHandler exposedFluidHandler = new SmartHopperFluidHandler();

	private SmartFluidTankBehaviour tank;
	private FilteringBehaviour filtering;
	private int transferCooldown;

	private final EnumMap<Direction, BlockCapabilityCache<IItemHandler, @Nullable Direction>> itemCapCaches =
		new EnumMap<>(Direction.class);
	private final EnumMap<Direction, BlockCapabilityCache<IFluidHandler, @Nullable Direction>> fluidCapCaches =
		new EnumMap<>(Direction.class);

	public SmartHopperBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AllBlockEntities.SMART_HOPPER.get(),
			(be, side) -> be.exposedItemHandler);
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AllBlockEntities.SMART_HOPPER.get(),
			(be, side) -> be.tank == null ? null : be.exposedFluidHandler);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(tank = SmartFluidTankBehaviour.single(this, FLUID_CAPACITY));
		behaviours.add(filtering = new FilteringBehaviour(this,
			new SmartHopperFilterSlotPositioning())
			.withCallback($ -> notifyUpdate()));
		filtering.setLabel(Component.translatable("block.fluidlogistics.smart_hopper.filter").copy());
	}

	@Override
	public void tick() {
		super.tick();

		if (level == null || level.isClientSide) {
			return;
		}

		if (transferCooldown > 0) {
			transferCooldown--;
		}

		if (!canActivate() || transferCooldown > 0) {
			return;
		}

		boolean transferred = false;

		transferred |= pushItems();
		transferred |= pullItems();
		transferred |= pushFluid();
		transferred |= pullFluid();

		if (transferred) {
			transferCooldown = TRANSFER_COOLDOWN_TICKS;
		} else {
			transferCooldown = FAILED_TRANSFER_COOLDOWN_TICKS;
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.put(INVENTORY_NBT_KEY, inventory.serializeNBT(registries));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (tag.contains(INVENTORY_NBT_KEY, Tag.TAG_COMPOUND)) {
			inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_NBT_KEY));
		}
	}

	@Override
	public void invalidate() {
		itemCapCaches.clear();
		fluidCapCaches.clear();
		super.invalidate();
	}

	@Override
	public void destroy() {
		super.destroy();
		ItemHelper.dropContents(level, worldPosition, inventory);
	}

	private boolean canActivate() {
		return !getBlockState().getValue(SmartHopperBlock.POWERED);
	}

	private Direction getFacing() {
		return getBlockState().getValue(SmartHopperBlock.FACING);
	}

	private boolean pushItems() {
		Direction facing = getFacing();
		BlockPos outputPos = worldPosition.relative(facing);

		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stackInSlot = inventory.getStackInSlot(slot);
			if (stackInSlot.isEmpty()) {
				continue;
			}
			if (filtering != null && !filtering.test(stackInSlot)) {
				continue;
			}

			ItemStack toExtract = stackInSlot.copyWithCount(ITEM_TRANSFER_AMOUNT);

			DirectBeltInputBehaviour beltBehaviour =
				BlockEntityBehaviour.get(level, outputPos, DirectBeltInputBehaviour.TYPE);
			if (beltBehaviour != null) {
				if (!beltBehaviour.canInsertFromSide(facing.getOpposite())) {
					continue;
				}
				ItemStack remainder = beltBehaviour.handleInsertion(toExtract, facing.getOpposite(), true);
				if (remainder.getCount() < toExtract.getCount()) {
					int inserted = toExtract.getCount() - remainder.getCount();
					ItemStack actualExtract = inventory.extractItem(slot, inserted, false);
					ItemStack actualRemainder = beltBehaviour.handleInsertion(actualExtract, facing.getOpposite(), false);
					int actuallyInserted = actualExtract.getCount() - actualRemainder.getCount();
					if (!actualRemainder.isEmpty()) {
						returnItemToInternal(slot, actualRemainder);
					}
					return actuallyInserted > 0;
				}
				continue;
			}

			IItemHandler destination = getItemCapability(facing, outputPos);
			if (destination == null) {
				continue;
			}

			ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(destination, toExtract, true);
			int canInsert = toExtract.getCount() - simulatedRemainder.getCount();
			if (canInsert <= 0) {
				continue;
			}

			ItemStack extracted = inventory.extractItem(slot, canInsert, false);
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, extracted, false);
			int actuallyInserted = extracted.getCount() - remainder.getCount();
			if (!remainder.isEmpty()) {
				returnItemToInternal(slot, remainder);
			}
			if (actuallyInserted <= 0) {
				continue;
			}
			return true;
		}
		return false;
	}

	private boolean pullItems() {
		BlockPos abovePos = worldPosition.above();
		TransportedItemStackHandlerBehaviour beltItems =
			BlockEntityBehaviour.get(level, abovePos, TransportedItemStackHandlerBehaviour.TYPE);
		if (beltItems != null) {
			return pullItemsFromBelt(beltItems);
		}

		IItemHandler source = getItemCapability(Direction.UP, abovePos);
		if (source == null) {
			return false;
		}

		for (int slot = 0; slot < source.getSlots(); slot++) {
			ItemStack stackInSlot = source.getStackInSlot(slot);
			if (stackInSlot.isEmpty()) {
				continue;
			}

			ItemStack simulatedExtract = source.extractItem(slot, ITEM_TRANSFER_AMOUNT, true);
			if (simulatedExtract.isEmpty()) {
				continue;
			}

			if (filtering != null && !filtering.test(simulatedExtract)) {
				continue;
			}

			ItemStack simulatedInsert = ItemHandlerHelper.insertItemStacked(inventory, simulatedExtract, true);
			if (simulatedInsert.getCount() >= simulatedExtract.getCount()) {
				continue;
			}

			int canInsert = simulatedExtract.getCount() - simulatedInsert.getCount();
			ItemStack extracted = source.extractItem(slot, canInsert, false);
			if (extracted.isEmpty()) {
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, extracted, false);
			int actuallyInserted = extracted.getCount() - remainder.getCount();
			if (!remainder.isEmpty()) {
				source.insertItem(slot, remainder, false);
			}
			if (actuallyInserted > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean pullItemsFromBelt(TransportedItemStackHandlerBehaviour beltItems) {
		boolean[] transferred = {false};
		beltItems.handleCenteredProcessingOnAllItems(.51f, transported -> {
			if (transferred[0]) {
				return TransportedResult.doNothing();
			}

			ItemStack stack = transported.stack;
			if (stack.isEmpty()) {
				return TransportedResult.doNothing();
			}
			if (filtering != null && !filtering.test(stack)) {
				return TransportedResult.doNothing();
			}

			ItemStack toInsert = stack.copyWithCount(Math.min(ITEM_TRANSFER_AMOUNT, stack.getCount()));
			ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(inventory, toInsert, true);
			int canInsert = toInsert.getCount() - simulatedRemainder.getCount();
			if (canInsert <= 0) {
				return TransportedResult.doNothing();
			}

			ItemStack actualInsert = toInsert.copyWithCount(canInsert);
			ItemStack actualRemainder = ItemHandlerHelper.insertItemStacked(inventory, actualInsert, false);
			int actuallyInserted = actualInsert.getCount() - actualRemainder.getCount();
			if (actuallyInserted <= 0) {
				return TransportedResult.doNothing();
			}

			transferred[0] = true;
			if (actuallyInserted >= stack.getCount()) {
				return TransportedResult.removeItem();
			}

			TransportedItemStack remaining = transported.copy();
			remaining.stack = stack.copyWithCount(stack.getCount() - actuallyInserted);
			return TransportedResult.convertTo(remaining);
		});
		return transferred[0];
	}

	private boolean pushFluid() {
		if (tank == null) {
			return false;
		}

		FluidStack stored = tank.getPrimaryHandler().getFluid();
		if (stored.isEmpty()) {
			return false;
		}

		if (filtering != null && !filtering.test(stored)) {
			return false;
		}

		Direction facing = getFacing();
		BlockPos outputPos = worldPosition.relative(facing);
		IFluidHandler destination = getFluidCapability(facing, outputPos);
		if (destination == null) {
			return false;
		}

		int transferAmount = Math.min(FLUID_TRANSFER_AMOUNT, stored.getAmount());
		FluidStack toTransfer = stored.copyWithAmount(transferAmount);
		int accepted = destination.fill(toTransfer, FluidAction.SIMULATE);
		if (accepted <= 0) {
			return false;
		}

		FluidStack drained = tank.getPrimaryHandler().drain(accepted, FluidAction.EXECUTE);
		if (drained.isEmpty()) {
			return false;
		}

		int filled = destination.fill(drained, FluidAction.EXECUTE);
		if (filled < drained.getAmount()) {
			FluidStack refund = drained.copyWithAmount(drained.getAmount() - filled);
			tank.getPrimaryHandler().fill(refund, FluidAction.EXECUTE);
		}
		if (filled <= 0) {
			notifyUpdate();
			return false;
		}
		notifyUpdate();
		return true;
	}

	private boolean pullFluid() {
		if (tank == null) {
			return false;
		}

		FluidStack stored = tank.getPrimaryHandler().getFluid();
		int space = FLUID_CAPACITY - stored.getAmount();
		if (space <= 0) {
			return false;
		}

		BlockPos abovePos = worldPosition.above();
		IFluidHandler source = getFluidCapability(Direction.UP, abovePos);
		if (source == null) {
			return false;
		}

		for (int t = 0; t < source.getTanks(); t++) {
			FluidStack candidate = source.getFluidInTank(t);
			if (candidate.isEmpty()) {
				continue;
			}
			if (!canAcceptFluid(candidate)) {
				continue;
			}

			int requestAmount = Math.min(Math.min(space, candidate.getAmount()), FLUID_TRANSFER_AMOUNT);
			FluidStack request = candidate.copyWithAmount(requestAmount);
			FluidStack simulated = source.drain(request, FluidAction.SIMULATE);
			if (simulated.isEmpty() || !canAcceptFluid(simulated)) {
				continue;
			}

			int canFill = tank.getPrimaryHandler().fill(simulated, FluidAction.SIMULATE);
			if (canFill <= 0) {
				continue;
			}

			FluidStack drained = source.drain(simulated.copyWithAmount(canFill), FluidAction.EXECUTE);
			if (drained.isEmpty()) {
				continue;
			}

			int filled = tank.getPrimaryHandler().fill(drained, FluidAction.EXECUTE);
			if (filled < drained.getAmount()) {
				FluidStack refund = drained.copyWithAmount(drained.getAmount() - filled);
				source.fill(refund, FluidAction.EXECUTE);
			}
			if (filled <= 0) {
				notifyUpdate();
				continue;
			}
			notifyUpdate();
			return true;
		}
		return false;
	}

	private boolean canAcceptFluid(FluidStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (filtering != null && !filtering.test(stack)) {
			return false;
		}
		if (tank == null) {
			return false;
		}
		FluidStack stored = tank.getPrimaryHandler().getFluid();
		return stored.isEmpty() || FluidStack.isSameFluidSameComponents(stored, stack);
	}

	@Nullable
	private IItemHandler getItemCapability(Direction facing, BlockPos targetPos) {
		if (level == null || !(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		BlockCapabilityCache<IItemHandler, @Nullable Direction> cache = itemCapCaches.get(facing);
		if (cache == null) {
			cache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, serverLevel, targetPos,
				facing.getOpposite());
			itemCapCaches.put(facing, cache);
		}
		return cache.getCapability();
	}

	@Nullable
	private IFluidHandler getFluidCapability(Direction facing, BlockPos targetPos) {
		if (level == null || !(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		BlockCapabilityCache<IFluidHandler, @Nullable Direction> cache = fluidCapCaches.get(facing);
		if (cache == null) {
			cache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, serverLevel, targetPos,
				facing.getOpposite());
			fluidCapCaches.put(facing, cache);
		}
		return cache.getCapability();
	}

	private void returnItemToInternal(int preferredSlot, ItemStack stack) {
		ItemStack remainder = inventory.insertItem(preferredSlot, stack, false);
		if (!remainder.isEmpty()) {
			ItemHandlerHelper.insertItemStacked(inventory, remainder, false);
		}
	}

	private class SmartHopperItemHandler implements IItemHandlerModifiable {

		@Override
		public int getSlots() {
			return inventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return inventory.getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return inventory.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return inventory.extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			if (filtering != null && !filtering.test(stack)) {
				return false;
			}
			return inventory.isItemValid(slot, stack);
		}

		@Override
		public void setStackInSlot(int slot, ItemStack stack) {
			inventory.setStackInSlot(slot, stack);
		}
	}

	private class SmartHopperFluidHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			IFluidHandler handler = getInternalHandler();
			return handler == null ? 0 : handler.getTanks();
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			IFluidHandler handler = getInternalHandler();
			return handler == null ? FluidStack.EMPTY : handler.getFluidInTank(tank);
		}

		@Override
		public int getTankCapacity(int tank) {
			IFluidHandler handler = getInternalHandler();
			return handler == null ? 0 : handler.getTankCapacity(tank);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return canAcceptFluid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			IFluidHandler handler = getInternalHandler();
			if (handler == null || !canAcceptFluid(resource)) {
				return 0;
			}
			return handler.fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			IFluidHandler handler = getInternalHandler();
			return handler == null ? FluidStack.EMPTY : handler.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			IFluidHandler handler = getInternalHandler();
			return handler == null ? FluidStack.EMPTY : handler.drain(maxDrain, action);
		}

		@Nullable
		private IFluidHandler getInternalHandler() {
			return tank == null ? null : tank.getCapability();
		}
	}
}

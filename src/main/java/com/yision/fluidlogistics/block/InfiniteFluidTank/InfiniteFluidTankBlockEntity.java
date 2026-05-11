package com.yision.fluidlogistics.block.InfiniteFluidTank;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.util.InfiniteFluidSupplyRules;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.function.Consumer;

public class InfiniteFluidTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int CAPACITY_BUCKETS = 10_000;
	public static final int CAPACITY = CAPACITY_BUCKETS * FluidType.BUCKET_VOLUME;
	private static final int SYNC_RATE = 8;

	protected InfiniteSmartFluidTank tankInventory;
	protected IFluidHandler fluidCapability;
	protected LerpedFloat fluidLevel;
	protected boolean forceFluidLevelUpdate;
	protected int syncCooldown;
	protected boolean queuedSync;
	protected int luminosity;

	public InfiniteFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tankInventory = new InfiniteSmartFluidTank(CAPACITY, this::onFluidStackChanged);
		forceFluidLevelUpdate = true;
		refreshCapability();
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			AllBlockEntities.INFINITE_FLUID_TANK.get(),
			(be, context) -> {
				if (be.fluidCapability == null)
					be.refreshCapability();
				return be.fluidCapability;
			}
		);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void initialize() {
		super.initialize();
		sendData();
	}

	@Override
	public void tick() {
		super.tick();
		if (syncCooldown > 0) {
			syncCooldown--;
			if (syncCooldown == 0 && queuedSync)
				sendData();
		}
		if (fluidLevel != null)
			fluidLevel.tickChaser();
	}

	protected void refreshCapability() {
		fluidCapability = tankInventory;
		invalidateCapabilities();
	}

	protected void onFluidStackChanged(FluidStack stack) {
		if (!hasLevel())
			return;

		int newLuminosity = stack.isEmpty() ? 0 : (int) (stack.getFluid().getFluidType().getLightLevel(stack) / 1.2f);
		if (!level.isClientSide && luminosity != newLuminosity) {
			luminosity = newLuminosity;
			level.getChunkSource().getLightEngine().checkBlock(worldPosition);
		}

		if (!level.isClientSide) {
			setChanged();
			sendData();
		}

		if (fluidLevel == null || forceFluidLevelUpdate)
			fluidLevel = LerpedFloat.linear().startWithValue(getFillState());
		fluidLevel.chase(getFillState(), .5f, LerpedFloat.Chaser.EXP);
	}

	public InfiniteSmartFluidTank getTankInventory() {
		return tankInventory;
	}

	public LerpedFloat getFluidLevel() {
		return fluidLevel;
	}

	public float getFillState() {
		return (float) tankInventory.getFluidAmount() / tankInventory.getCapacity();
	}

	public boolean isInfiniteMode() {
		return tankInventory.isInfiniteMode();
	}

	public int getComparatorOutput() {
		return net.minecraft.util.Mth.floor(getFillState() * 14.0F) + (tankInventory.isEmpty() ? 0 : 1);
	}

	public void sendDataImmediately() {
		syncCooldown = 0;
		queuedSync = false;
		sendData();
	}

	@Override
	public void sendData() {
		if (syncCooldown > 0) {
			queuedSync = true;
			return;
		}
		super.sendData();
		queuedSync = false;
		syncCooldown = SYNC_RATE;
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		luminosity = compound.getInt("Luminosity");
		tankInventory.setCapacity(CAPACITY);
		readTankContent(compound.getCompound("TankContent"), registries);
		tankInventory.clampToCapacity();

		if (compound.contains("ForceFluidLevel") || fluidLevel == null)
			fluidLevel = LerpedFloat.linear().startWithValue(getFillState());

		if (clientPacket && fluidLevel != null)
			fluidLevel.chase(getFillState(), .5f, LerpedFloat.Chaser.EXP);
	}

	@Override
	public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		compound.put("TankContent", tankInventory.getFluid()
			.saveOptional(registries));
		compound.putInt("Luminosity", luminosity);
		super.write(compound, registries, clientPacket);

		if (!clientPacket)
			return;
		if (forceFluidLevelUpdate)
			compound.putBoolean("ForceFluidLevel", true);
		if (queuedSync)
			compound.putBoolean("LazySync", true);
		forceFluidLevelUpdate = false;
	}

	private void readTankContent(CompoundTag tankContent, HolderLookup.Provider registries) {
		tankInventory.setFluid(FluidStack.parseOptional(registries, tankContent));
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (!hasLevel())
			return;
		CompoundTag blockEntityData = saveCustomOnly(level.registryAccess());
		blockEntityData.remove("id");
		if (!blockEntityData.isEmpty()) {
			net.minecraft.world.level.block.entity.BlockEntity.addEntityType(blockEntityData, getType());
			components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(blockEntityData));
		}
	}

	@Override
	public void removeComponentsFromTag(CompoundTag tag) {
		tag.remove("TankContent");
		tag.remove("Luminosity");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (isInfiniteMode()) {
			containedFluidTooltip(tooltip, isPlayerSneaking, tankInventory);
			CreateLang.translate("hint.hose_pulley.title")
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip);
			return true;
		}
		return containedFluidTooltip(tooltip, isPlayerSneaking, tankInventory);
	}

	public static class InfiniteSmartFluidTank extends SmartFluidTank {

		public InfiniteSmartFluidTank(int capacity, Consumer<FluidStack> updateCallback) {
			super(capacity, updateCallback);
		}

		public boolean isInfiniteMode() {
			return InfiniteFluidSupplyRules.isInfiniteSupply(fluid, capacity);
		}

		public boolean isEmpty() {
			return fluid.isEmpty();
		}

		public void clampToCapacity() {
			if (!fluid.isEmpty() && fluid.getAmount() > capacity)
				fluid.setAmount(capacity);
		}

		@Override
		public boolean isFluidValid(FluidStack stack) {
			if (!InfiniteFluidSupplyRules.canEnterInfiniteTank(stack))
				return false;
			return fluid.isEmpty() || FluidStack.isSameFluidSameComponents(fluid, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || !isFluidValid(resource))
				return 0;

			if (isInfiniteMode())
				return resource.getAmount();

			int space = capacity - fluid.getAmount();
			if (space <= 0)
				return 0;

			int accepted = Math.min(space, resource.getAmount());
			if (action.execute()) {
				if (fluid.isEmpty()) {
					fluid = resource.copy();
					fluid.setAmount(accepted);
				} else {
					fluid.grow(accepted);
				}
				if (fluid.getAmount() > capacity)
					fluid.setAmount(capacity);
				onContentsChanged();
			}
			return accepted;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || fluid.isEmpty())
				return FluidStack.EMPTY;
			if (!FluidStack.isSameFluidSameComponents(resource, fluid))
				return FluidStack.EMPTY;
			return drain(resource.getAmount(), action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (maxDrain <= 0 || !isInfiniteMode())
				return FluidStack.EMPTY;
			FluidStack result = fluid.copy();
			result.setAmount(maxDrain);
			return result;
		}
	}
}

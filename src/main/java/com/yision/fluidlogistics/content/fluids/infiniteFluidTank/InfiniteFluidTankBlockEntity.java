package com.yision.fluidlogistics.content.fluids.infiniteFluidTank;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidSupplyRules;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class InfiniteFluidTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	private static final int SYNC_RATE = 8;

	protected InfiniteSmartFluidTank tankInventory;
	protected LazyOptional<IFluidHandler> fluidCapability;
	protected LerpedFloat fluidLevel;
	protected boolean forceFluidLevelUpdate;
	protected int syncCooldown;
	protected boolean queuedSync;
	protected int luminosity;

	public InfiniteFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tankInventory = new InfiniteSmartFluidTank(InfiniteFluidSupplyRules.getRequiredAmount(), this::onFluidStackChanged);
		fluidCapability = LazyOptional.of(() -> tankInventory);
		forceFluidLevelUpdate = true;
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
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		luminosity = compound.getInt("Luminosity");
		tankInventory.setCapacity(InfiniteFluidSupplyRules.getRequiredAmount());
		readTankContent(compound.getCompound("TankContent"));
		tankInventory.clampToCapacity();

		if (compound.contains("ForceFluidLevel") || fluidLevel == null)
			fluidLevel = LerpedFloat.linear().startWithValue(getFillState());

		if (clientPacket && fluidLevel != null)
			fluidLevel.chase(getFillState(), .5f, LerpedFloat.Chaser.EXP);
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		compound.put("TankContent", tankInventory.getFluid().writeToNBT(new CompoundTag()));
		compound.putInt("Luminosity", luminosity);
		super.write(compound, clientPacket);

		if (!clientPacket)
			return;
		if (forceFluidLevelUpdate)
			compound.putBoolean("ForceFluidLevel", true);
		if (queuedSync)
			compound.putBoolean("LazySync", true);
		forceFluidLevelUpdate = false;
	}

	private void readTankContent(CompoundTag tankContent) {
		tankInventory.setFluid(FluidStack.loadFluidStackFromNBT(tankContent));
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCapability.invalidate();
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER)
			return fluidCapability.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (!InfiniteFluidSupplyRules.isInfiniteSourceEnabled() && !tankInventory.getFluid().isEmpty())
			return disabledInfiniteSourceFluidTooltip(tooltip);

		if (isInfiniteMode()) {
			containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability);
			CreateLang.translate("hint.hose_pulley.title")
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip);
			return true;
		}
		return containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability);
	}

	private boolean disabledInfiniteSourceFluidTooltip(List<Component> tooltip) {
		FluidStack fluid = tankInventory.getFluid();

		CreateLang.translate("gui.goggles.fluid_container")
			.forGoggles(tooltip);
		CreateLang.fluidName(fluid)
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip, 1);
		CreateLang.builder()
			.add(CreateLang.number(fluid.getAmount())
				.text("mB")
				.style(ChatFormatting.GOLD))
			.text(ChatFormatting.GRAY, " / ")
			.add(Component.literal(InfiniteFluidSupplyRules.getRequiredAmountText())
				.withStyle(ChatFormatting.DARK_GRAY))
			.forGoggles(tooltip, 1);
		return true;
	}

	public static class InfiniteSmartFluidTank extends SmartFluidTank {

		public InfiniteSmartFluidTank(int capacity, Consumer<FluidStack> updateCallback) {
			super(capacity, updateCallback);
		}

		public boolean isInfiniteMode() {
			return InfiniteFluidSupplyRules.isInfiniteSupply(fluid);
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
			return fluid.isEmpty() || (fluid.isFluidEqual(stack) && FluidStack.areFluidStackTagsEqual(fluid, stack));
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
			if (!(resource.isFluidEqual(fluid) && FluidStack.areFluidStackTagsEqual(resource, fluid)))
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

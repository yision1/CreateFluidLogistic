package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.block.FluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.block.Faucet.FaucetFilling;
import com.yision.fluidlogistics.config.FeatureToggle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

class MechanicalFluidGunFillOperations {

	static final int TRANSFER_RATE = 250;
	static final int BUCKET_AMOUNT = 1000;

	private MechanicalFluidGunFillOperations() {
	}

	@Nullable
	static IFluidHandler getTargetFluidHandler(Level level, BlockPos pos, @Nullable Direction face) {
		// Fluid hatch special handling - must come before normal capability lookup
		BlockState state = level.getBlockState(pos);
		IFluidHandler hatchHandler = FluidHatchFluidHandlerForwarder.get(level, pos, state, face);
		if (hatchHandler != null) {
			return hatchHandler;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) return null;
		IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
		if (handler == null) {
			handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
		}
		return handler;
	}

	static FluidStack findFillableFluidForItem(MechanicalFluidGunContext ctx, IFluidHandler sourceHandler,
											   ItemStack item) {
		for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
			FluidStack candidate = sourceHandler.getFluidInTank(tank);
			if (candidate.isEmpty() || !ctx.testFilter(candidate)) continue;
			int requiredAmount = FaucetFilling.getRequiredAmountForItem(ctx.level(), item, candidate.copy());
			if (requiredAmount > 0 && requiredAmount <= candidate.getAmount()) {
				return candidate.copy();
			}
		}
		return FluidStack.EMPTY;
	}

	static FluidStack findFillableFluidForContainer(MechanicalFluidGunContext ctx, IFluidHandler sourceHandler,
														IFluidHandler targetHandler, BlockPos targetPos) {
		for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
			FluidStack candidate = sourceHandler.getFluidInTank(tank);
			if (candidate.isEmpty() || !ctx.testFilter(candidate)) continue;

			FluidStack preview = FluidHelper.copyStackWithAmount(candidate, Math.min(candidate.getAmount(), TRANSFER_RATE));
			int accepted = targetHandler.fill(preview, IFluidHandler.FluidAction.SIMULATE);
			if (accepted <= 0) continue;

			if (!ctx.canFillFluidContainer(targetPos, targetHandler, preview)) continue;

			return FluidHelper.copyStackWithAmount(preview, Math.min(preview.getAmount(), accepted));
		}
		ctx.finishFluidContainerFill(targetPos);
		return FluidStack.EMPTY;
	}

	static boolean isFluidContainerBelowHalf(IFluidHandler targetHandler, FluidStack candidate) {
		if (candidate.isEmpty()) return false;

		int amount = 0;
		int capacity = 0;
		for (int tank = 0; tank < targetHandler.getTanks(); tank++) {
			int tankCapacity = targetHandler.getTankCapacity(tank);
			if (tankCapacity <= 0) continue;

			FluidStack existing = targetHandler.getFluidInTank(tank);
			if (existing.isEmpty()) {
				capacity += tankCapacity;
				continue;
			}

			if (existing.isFluidEqual(candidate) && FluidStack.areFluidStackTagsEqual(existing, candidate)) {
				amount += existing.getAmount();
				capacity += tankCapacity;
			}
		}

		return capacity > 0 && amount * 2 < capacity;
	}

	static FluidStack findFillableFluidForCauldron(MechanicalFluidGunContext ctx, IFluidHandler sourceHandler,
												   BlockState targetState) {
		for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
			FluidStack candidate = sourceHandler.getFluidInTank(tank);
			if (candidate.isEmpty() || !ctx.testFilter(candidate)) continue;
			if (canFillCauldron(targetState, candidate)) {
				return FluidHelper.copyStackWithAmount(candidate, Math.min(candidate.getAmount(), 1000));
			}
		}
		return FluidStack.EMPTY;
	}

	static boolean canFillCauldron(BlockState targetState, FluidStack availableFluid) {
		if (availableFluid.isEmpty()) return false;
		if (availableFluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER) {
			if (targetState.is(Blocks.CAULDRON)) return availableFluid.getAmount() >= 250;
			if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
				int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
				return currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL && availableFluid.getAmount() >= 250;
			}
			return false;
		}
		if (!targetState.is(Blocks.CAULDRON)) return false;
		var cauldronInfo = com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior.CAULDRON_INFO
			.get(availableFluid.getFluid());
		return cauldronInfo != null && availableFluid.getAmount() >= cauldronInfo.amount();
	}

	static boolean tryFillContainerWithActiveTarget(MechanicalFluidGunContext ctx, MechanicalFluidGunVisuals visuals,
													MechanicalFluidGunTargetConfig target, BlockPos absTarget) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) return false;
		Level level = ctx.level();
		IFluidHandler sourceHandler = ctx.sourceHandler();
		if (sourceHandler == null) return false;

		var targetEntity = level.getBlockEntity(absTarget);
		if (targetEntity == null) return false;

		IFluidHandler targetHandler = getTargetFluidHandler(level, targetEntity.getBlockPos(), target.face());
		if (targetHandler == null) return false;

		FluidStack availableFluid = findFillableFluidForContainer(ctx, sourceHandler, targetHandler, absTarget);
		if (availableFluid.isEmpty()) return false;

		FluidStack toTransfer = FluidHelper.copyStackWithAmount(availableFluid, Math.min(availableFluid.getAmount(), TRANSFER_RATE));
		int filled = targetHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
		if (filled <= 0) return false;

		FluidStack toDrain = FluidHelper.copyStackWithAmount(toTransfer, filled);
		FluidStack actualDrain = sourceHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
		if (actualDrain.isEmpty()) return false;

		int actuallyFilled = targetHandler.fill(actualDrain, IFluidHandler.FluidAction.EXECUTE);
		if (actuallyFilled < actualDrain.getAmount()) {
			FluidStack surplus = FluidHelper.copyStackWithAmount(actualDrain, actualDrain.getAmount() - actuallyFilled);
			if (!restoreToSource(sourceHandler, surplus)) {
				return false;
			}
		}
		if (actuallyFilled <= 0) return false;
		FluidStack transferred = FluidHelper.copyStackWithAmount(actualDrain, Math.min(actualDrain.getAmount(), actuallyFilled));
		ctx.markFluidContainerFilled(absTarget);

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(absTarget);
		visuals.startSpraying(transferred, ctx.speed());
		visuals.spawnServerSprayParticles(level, ctx.gunPos(), aimPoint);

		if (level.random.nextFloat() < 0.1f) {
			AllSoundEvents.SPOUTING.playOnServer(level, ctx.gunPos(), 0.3f, 0.9f + level.random.nextFloat() * 0.2f);
		}

		ctx.notifyGunUpdate();
		return true;
	}

	static boolean tryFillCauldron(MechanicalFluidGunContext ctx, MechanicalFluidGunVisuals visuals,
								  BlockPos targetPos, BlockState targetState, FluidStack availableFluid) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) return false;
		Level level = ctx.level();
		IFluidHandler sourceHandler = ctx.sourceHandler();
		if (sourceHandler == null) return false;

		if (availableFluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER) {
			if (targetState.is(Blocks.CAULDRON)) {
				return fillWaterCauldronLevel(ctx, visuals, targetPos, 1);
			}
			if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
				int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
				if (currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL) {
					return fillWaterCauldronLevel(ctx, visuals, targetPos, currentLevel + 1);
				}
			}
			return false;
		}

		if (!targetState.is(Blocks.CAULDRON)) return false;

		var cauldronInfo = com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior.CAULDRON_INFO
			.get(availableFluid.getFluid());
		if (cauldronInfo == null || availableFluid.getAmount() < cauldronInfo.amount()) return false;

		FluidStack toDrain = FluidHelper.copyStackWithAmount(availableFluid, cauldronInfo.amount());
		FluidStack drained = sourceHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() < cauldronInfo.amount()) return false;

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(targetPos);
		level.setBlockAndUpdate(targetPos, cauldronInfo.cauldron());
		visuals.startSpraying(drained, ctx.speed());
		visuals.spawnServerSprayParticles(level, ctx.gunPos(), aimPoint);
		level.playSound(null, targetPos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5f, 1.0f);
		ctx.notifyGunUpdate();
		return true;
	}

	static boolean fillWaterCauldronLevel(MechanicalFluidGunContext ctx, MechanicalFluidGunVisuals visuals,
										  BlockPos targetPos, int targetLevel) {
		IFluidHandler sourceHandler = ctx.sourceHandler();
		if (sourceHandler == null) return false;

		FluidStack drained = sourceHandler.drain(
			new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 250),
			IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() < 250) return false;

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(targetPos);
		ctx.level().setBlockAndUpdate(targetPos,
			Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, targetLevel));
		visuals.startSpraying(drained, ctx.speed());
		visuals.spawnServerSprayParticles(ctx.level(), ctx.gunPos(), aimPoint);
		ctx.level().playSound(null, targetPos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5f, 0.8f + targetLevel * 0.1f);
		ctx.notifyGunUpdate();
		return true;
	}

	static boolean canFuel(MechanicalFluidGunContext ctx, IFluidHandler source,
						   BlockState state, BlockPos pos) {
		if (!isBlazeBurnerWithEntity(ctx.level(), state, pos)) return false;
		return !findFuelFluid(ctx, source, state, pos).isEmpty();
	}

	static boolean tryFuel(MechanicalFluidGunContext ctx, MechanicalFluidGunVisuals visuals,
						   IFluidHandler source, BlockState state, BlockPos pos) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) return false;
		if (!isBlazeBurnerWithEntity(ctx.level(), state, pos)) return false;

		FluidStack fuel = findFuelFluid(ctx, source, state, pos);
		if (fuel.isEmpty()) return false;

		FluidStack simulatedDrain = source.drain(fuel, IFluidHandler.FluidAction.SIMULATE);
		if (simulatedDrain.getAmount() < BUCKET_AMOUNT) return false;

		FluidStack drained = source.drain(fuel, IFluidHandler.FluidAction.EXECUTE);
		if (drained.getAmount() < BUCKET_AMOUNT) {
			if (!drained.isEmpty() && !restoreToSource(source, drained)) {
				return false;
			}
			return false;
		}

		Item bucketItem = drained.getFluid().getBucket();
		ItemStack bucketStack = new ItemStack(bucketItem);
		InteractionResultHolder<ItemStack> result =
			BlazeBurnerBlock.tryInsert(state, ctx.level(), pos, bucketStack, true, false, false);
		if (result.getResult() != InteractionResult.SUCCESS) {
			if (!restoreToSource(source, drained)) {
				return false;
			}
			return false;
		}

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(pos);
		visuals.startSpraying(drained, ctx.speed());
		visuals.spawnServerSprayParticles(ctx.level(), ctx.gunPos(), aimPoint);
		ctx.notifyGunUpdate();
		return true;
	}

	static FluidStack findFuelFluid(MechanicalFluidGunContext ctx, IFluidHandler source,
									 BlockState state, BlockPos pos) {
		for (int tank = 0; tank < source.getTanks(); tank++) {
			FluidStack candidate = source.getFluidInTank(tank);
			if (candidate.isEmpty() || candidate.getAmount() < BUCKET_AMOUNT) continue;
			if (!ctx.testFilter(candidate)) continue;

			Item bucket = candidate.getFluid().getBucket();
			if (bucket == Items.AIR) continue;

			ItemStack bucketStack = new ItemStack(bucket);
			if (BlazeBurnerBlock.tryInsert(state, ctx.level(), pos, bucketStack, true, false, true)
				.getResult() == InteractionResult.SUCCESS) {
				return FluidHelper.copyStackWithAmount(candidate, BUCKET_AMOUNT);
			}
		}
		return FluidStack.EMPTY;
	}

	private static boolean restoreToSource(IFluidHandler source, FluidStack stack) {
		if (stack.isEmpty()) return true;
		int restored = source.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
		return restored >= stack.getAmount();
	}

	private static boolean isBlazeBurnerWithEntity(Level level, BlockState state, BlockPos pos) {
		if (!AllBlocks.BLAZE_BURNER.has(state)) return false;
		if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE) return false;
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof BlazeBurnerBlockEntity;
	}
}

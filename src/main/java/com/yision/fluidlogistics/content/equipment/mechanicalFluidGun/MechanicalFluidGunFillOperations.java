package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.foundation.fluid.CauldronFills;
import com.yision.fluidlogistics.foundation.fluid.FluidSourceScans;

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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

class MechanicalFluidGunFillOperations {

	static final int TRANSFER_RATE = 250;
	static final int BUCKET_AMOUNT = 1000;

	private MechanicalFluidGunFillOperations() {
	}

	@Nullable
	static IFluidHandler getTargetFluidHandler(Level level, BlockPos pos, @Nullable Direction face) {
		BlockState targetState = level.getBlockState(pos);
		IFluidHandler hatchHandler = FluidHatchFluidHandlerForwarder.getForMechanicalFluidGun(level, pos, targetState, face);
		if (hatchHandler != null) {
			return hatchHandler;
		}
		IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
		if (handler == null) {
			handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
		}
		return handler;
	}

	static FluidStack findFillableFluidForItem(MechanicalFluidGunContext ctx, IFluidHandler sourceHandler,
											   ItemStack item) {
		return FluidSourceScans.findForItem(ctx.level(), sourceHandler, ctx::testFilter, item, false);
	}

	static FluidStack findFillableFluidForContainer(MechanicalFluidGunContext ctx, IFluidHandler sourceHandler,
															IFluidHandler targetHandler, BlockPos targetPos) {
		FluidStack fillable = FluidSourceScans.findForContainer(sourceHandler, ctx::testFilter, targetHandler,
			TRANSFER_RATE, candidate -> ctx.canFillFluidContainer(targetPos, targetHandler, candidate), false);
		if (!fillable.isEmpty()) return fillable;
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

			if (FluidStack.isSameFluidSameComponents(existing, candidate)) {
				amount += existing.getAmount();
				capacity += tankCapacity;
			}
		}

		return capacity > 0 && amount * 2 < capacity;
	}

	static FluidStack findFillableFluidForCauldron(MechanicalFluidGunContext ctx, IFluidHandler sourceHandler,
												   BlockState targetState) {
		return FluidSourceScans.findForCauldron(sourceHandler, ctx::testFilter, targetState, 1000, false);
	}

	static boolean tryFillContainerWithActiveTarget(MechanicalFluidGunContext ctx, MechanicalFluidGunVisuals visuals,
													MechanicalFluidGunTargetConfig target, BlockPos absTarget) {
		Level level = ctx.level();
		IFluidHandler sourceHandler = ctx.sourceHandler();
		if (sourceHandler == null) return false;

		var targetEntity = level.getBlockEntity(absTarget);
		if (targetEntity == null) return false;

		IFluidHandler targetHandler = getTargetFluidHandler(level, targetEntity.getBlockPos(), target.face());
		if (targetHandler == null) return false;

		FluidStack availableFluid = findFillableFluidForContainer(ctx, sourceHandler, targetHandler, absTarget);
		if (availableFluid.isEmpty()) return false;

		return tryFillContainer(ctx, visuals, absTarget, sourceHandler, targetHandler, availableFluid);
	}

	static boolean tryFillContainer(MechanicalFluidGunContext ctx, MechanicalFluidGunVisuals visuals,
									BlockPos absTarget, IFluidHandler sourceHandler, IFluidHandler targetHandler,
									FluidStack availableFluid) {
		Level level = ctx.level();
		FluidStack toTransfer = availableFluid.copyWithAmount(Math.min(availableFluid.getAmount(), TRANSFER_RATE));
		int filled = targetHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
		if (filled <= 0) return false;

		FluidStack actualDrain = sourceHandler.drain(toTransfer.copyWithAmount(filled), IFluidHandler.FluidAction.EXECUTE);
		if (actualDrain.isEmpty()) return false;

		int actuallyFilled = targetHandler.fill(actualDrain, IFluidHandler.FluidAction.EXECUTE);
		if (actuallyFilled < actualDrain.getAmount()) {
			FluidStack surplus = actualDrain.copyWithAmount(actualDrain.getAmount() - actuallyFilled);
			if (!restoreToSource(sourceHandler, surplus)) {
				return false;
			}
		}
		if (actuallyFilled <= 0) return false;
		FluidStack transferred = actualDrain.copyWithAmount(Math.min(actualDrain.getAmount(), actuallyFilled));
		ctx.markFluidContainerFilled(absTarget);

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(level, absTarget);
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
		Level level = ctx.level();
		IFluidHandler sourceHandler = ctx.sourceHandler();
		if (sourceHandler == null) return false;

		int targetLevel = 0;
		if (availableFluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER) {
			targetLevel = targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)
				? targetState.getValue(LayeredCauldronBlock.LEVEL) + 1
				: 1;
		}
		FluidStack drained = CauldronFills.fill(level, sourceHandler, targetPos, targetState, availableFluid);
		if (drained.isEmpty()) return false;

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(level, targetPos);
		visuals.startSpraying(drained, ctx.speed());
		visuals.spawnServerSprayParticles(level, ctx.gunPos(), aimPoint);
		level.playSound(null, targetPos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5f,
			targetLevel > 0 ? 0.8f + targetLevel * 0.1f : 1.0f);
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

		Vec3 aimPoint = MechanicalFluidGunTarget.getTargetCenter(ctx.level(), pos);
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
				return candidate.copyWithAmount(BUCKET_AMOUNT);
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

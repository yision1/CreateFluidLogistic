package com.yision.fluidlogistics.block.InfiniteFluidTank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class InfiniteFluidTankBlock extends Block implements IWrenchable, IBE<InfiniteFluidTankBlockEntity> {

	public InfiniteFluidTankBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
	                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
		boolean onClient = level.isClientSide;

		if (stack.isEmpty())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		InfiniteFluidTankBlockEntity be = getBlockEntity(level, pos);
		if (be == null)
			return ItemInteractionResult.FAIL;

		IFluidHandler tankCapability = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
		if (tankCapability == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		FluidStack prevFluidInTank = tankCapability.getFluidInTank(0).copy();
		FluidExchange exchange = null;

		if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be))
			exchange = FluidExchange.ITEM_TO_TANK;
		else if (FluidHelper.tryFillItemFromBE(level, player, hand, stack, be))
			exchange = FluidExchange.TANK_TO_ITEM;

		if (exchange == null) {
			if (GenericItemEmptying.canItemBeEmptied(level, stack)
				|| GenericItemFilling.canItemBeFilled(level, stack))
				return ItemInteractionResult.SUCCESS;
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		FluidStack fluidInTank = tankCapability.getFluidInTank(0);
		SoundEvent soundEvent = null;
		BlockState fluidState = null;

		if (exchange == FluidExchange.ITEM_TO_TANK && !fluidInTank.isEmpty()) {
			Fluid fluid = fluidInTank.getFluid();
			fluidState = fluid.defaultFluidState().createLegacyBlock();
			soundEvent = FluidHelper.getEmptySound(fluidInTank);
		}

		if (exchange == FluidExchange.TANK_TO_ITEM && !prevFluidInTank.isEmpty()) {
			Fluid fluid = prevFluidInTank.getFluid();
			fluidState = fluid.defaultFluidState().createLegacyBlock();
			soundEvent = FluidHelper.getFillSound(prevFluidInTank);
		}

		if (soundEvent != null && !onClient) {
			float pitch = Mth.clamp(1 - be.getFillState(), 0, 1);
			pitch /= 1.5f;
			pitch += .5f;
			pitch += (level.random.nextFloat() - .5f) / 4f;
			level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, .5f, pitch);
		}

		if (!FluidStack.isSameFluidSameComponents(fluidInTank, prevFluidInTank)
			|| fluidInTank.getAmount() != prevFluidInTank.getAmount()) {
			if (fluidState != null && onClient) {
				BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, fluidState);
				float fluidLevel = be.getFillState();
				boolean reversed = fluidInTank.getFluid().getFluidType().isLighterThanAir();
				if (reversed)
					fluidLevel = 1 - fluidLevel;

				Vec3 vec = hitResult.getLocation();
				vec = new Vec3(vec.x, pos.getY() + fluidLevel * .5f + .25f, vec.z);
				Vec3 motion = player.position().subtract(vec).scale(1 / 20f);
				vec = vec.add(motion);
				level.addParticle(particle, vec.x, vec.y, vec.z, motion.x, motion.y, motion.z);
				return ItemInteractionResult.SUCCESS;
			}

			be.sendDataImmediately();
			be.setChanged();
		}

		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
			level.removeBlockEntity(pos);
		}
	}

	@Override
	public Class<InfiniteFluidTankBlockEntity> getBlockEntityClass() {
		return InfiniteFluidTankBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends InfiniteFluidTankBlockEntity> getBlockEntityType() {
		return AllBlockEntities.INFINITE_FLUID_TANK.get();
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		InfiniteFluidTankBlockEntity be = getBlockEntity(level, pos);
		return be == null ? 0 : be.getComparatorOutput();
	}
}

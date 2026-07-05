package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public abstract class AbstractMultiFluidTankBlock<T extends AbstractMultiFluidTankBlockEntity<T>>
		extends Block implements IWrenchable, IBE<T> {

	public static final VoxelShape CAMPFIRE_SMOKE_CLIP = Block.box(0, 4, 0, 16, 16, 16);

	protected AbstractMultiFluidTankBlock(Properties properties) {
		super(properties);
	}

	protected abstract String silenceSoundTag();

	@Override
	public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
		return 0;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		if (context == CollisionContext.empty())
			return CAMPFIRE_SMOKE_CLIP;
		return state.getShape(level, pos);
	}

	@Override
	public VoxelShape getBlockSupportShape(BlockState state, BlockGetter reader, BlockPos pos) {
		return Shapes.block();
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
			net.minecraft.world.level.LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
		return state;
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean moved) {
		if (oldState.getBlock() == state.getBlock())
			return;
		if (moved)
			return;
		withBlockEntityDo(world, pos, AbstractMultiFluidTankBlockEntity::scheduleConnectivityUpdate);
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (!isOwnBlockEntity(be))
				return;
			world.removeBlockEntity(pos);
			ConnectivityHandler.splitMulti((BlockEntity & com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer) be);
		}
	}

	protected abstract boolean isOwnBlockEntity(BlockEntity be);

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos) {
		return getBlockEntityOptional(worldIn, pos).map(AbstractMultiFluidTankBlockEntity::getControllerBE)
				.map(AbstractMultiFluidTankBlockEntity::getComparatorOutput)
				.orElse(0);
	}

	@Override
	public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		boolean onClient = level.isClientSide;

		if (stack.isEmpty())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		FluidExchange exchange = null;
		T be = ConnectivityHandler.partAt(getBlockEntityType(), level, pos);
		if (be == null)
			return ItemInteractionResult.FAIL;

		IFluidHandler tankCapability = level.getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), null);
		if (tankCapability == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		FluidStack prevFluidInTank = tankCapability.getFluidInTank(0).copy();

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

		SoundEvent soundevent = null;
		BlockState fluidState = null;
		FluidStack fluidInTank = tankCapability.getFluidInTank(0);

		if (exchange == FluidExchange.ITEM_TO_TANK) {
			net.minecraft.world.level.material.Fluid fluid = fluidInTank.getFluid();
			fluidState = fluid.defaultFluidState().createLegacyBlock();
			soundevent = FluidHelper.getEmptySound(fluidInTank);
		}

		if (exchange == FluidExchange.TANK_TO_ITEM) {
			net.minecraft.world.level.material.Fluid fluid = prevFluidInTank.getFluid();
			fluidState = fluid.defaultFluidState().createLegacyBlock();
			soundevent = FluidHelper.getFillSound(prevFluidInTank);
		}

		if (soundevent != null && !onClient) {
			float pitch = Mth.clamp(
					1 - (1f * fluidInTank.getAmount() / (AbstractMultiFluidTankBlockEntity.getCapacityMultiplier() * 16)),
					0, 1);
			pitch /= 1.5f;
			pitch += .5f;
			pitch += (level.random.nextFloat() - .5f) / 4f;
			level.playSound(null, pos, soundevent, SoundSource.BLOCKS, .5f, pitch);
		}

		if (!FluidStack.isSameFluidSameComponents(fluidInTank, prevFluidInTank)) {
			T controllerBE = be.getControllerBE();
			if (controllerBE != null) {
				if (fluidState != null && onClient) {
					BlockParticleOption blockParticleData = new BlockParticleOption(ParticleTypes.BLOCK, fluidState);
					float fluidLevel = (float) fluidInTank.getAmount() / tankCapability.getTankCapacity(0);

					boolean reversed = fluidInTank.getFluid().getFluidType().isLighterThanAir();
					if (reversed)
						fluidLevel = 1 - fluidLevel;

					Vec3 vec = hitResult.getLocation();
					vec = new Vec3(vec.x, controllerBE.getBlockPos().getY() + fluidLevel * particleHeight(controllerBE) + .25f, vec.z);
					Vec3 motion = player.position().subtract(vec).scale(1 / 20f);
					vec = vec.add(motion);
					level.addParticle(blockParticleData, vec.x, vec.y, vec.z, motion.x, motion.y, motion.z);
					return ItemInteractionResult.SUCCESS;
				}

				controllerBE.sendDataImmediately();
				controllerBE.setChanged();
			}
		}

		return ItemInteractionResult.SUCCESS;
	}

	protected abstract float particleHeight(T controllerBE);

	public static final SoundType SILENCED_METAL =
			new DeferredSoundType(0.1F, 1.5F, () -> SoundEvents.METAL_BREAK, () -> SoundEvents.METAL_STEP,
					() -> SoundEvents.METAL_PLACE, () -> SoundEvents.METAL_HIT, () -> SoundEvents.METAL_FALL);

	@Override
	public SoundType getSoundType(BlockState state, LevelReader world, BlockPos pos, Entity entity) {
		SoundType soundType = super.getSoundType(state, world, pos, entity);
		if (entity != null && entity.getPersistentData().contains(silenceSoundTag()))
			return SILENCED_METAL;
		return soundType;
	}

	@Override
	public abstract BlockEntityType<? extends T> getBlockEntityType();
}

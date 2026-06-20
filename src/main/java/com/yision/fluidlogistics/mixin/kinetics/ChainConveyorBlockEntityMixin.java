package com.yision.fluidlogistics.mixin.kinetics;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.PhantomChainConveyorAccess;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public class ChainConveyorBlockEntityMixin implements PhantomChainConveyorAccess {

	@Shadow
	public Set<BlockPos> connections;

	@Unique
	private final Set<BlockPos> fluidlogistics$phantomConnections = new HashSet<>();

	@Unique
	private static final String fluidlogistics$PHANTOM_CONNECTIONS_KEY = "FluidLogisticsPhantomConnections";

	@Override
	public boolean fluidlogistics$isPhantomConnection(BlockPos connection) {
		return fluidlogistics$phantomConnections.contains(connection);
	}

	@Override
	public void fluidlogistics$setPhantomConnection(BlockPos connection, boolean phantom) {
		if (phantom) {
			if (connections.contains(connection)) {
				fluidlogistics$phantomConnections.add(connection);
			}
		} else {
			fluidlogistics$phantomConnections.remove(connection);
		}
	}

	@Inject(method = "writeSafe", at = @At("HEAD"), remap = false)
	private void fluidlogistics$writeSafe(CompoundTag tag, CallbackInfo ci) {
		fluidlogistics$writePhantomConnections(tag);
	}

	@Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Z)V", at = @At("HEAD"), remap = false)
	private void fluidlogistics$write(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
		fluidlogistics$writePhantomConnections(compound);
	}

	@Unique
	private void fluidlogistics$writePhantomConnections(CompoundTag tag) {
		tag.put(fluidlogistics$PHANTOM_CONNECTIONS_KEY,
			NBTHelper.writeCompoundList(fluidlogistics$phantomConnections, NbtUtils::writeBlockPos));
	}

	@Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Z)V", at = @At("RETURN"), remap = false)
	private void fluidlogistics$read(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
		fluidlogistics$phantomConnections.clear();
		NBTHelper.iterateCompoundList(compound.getList(fluidlogistics$PHANTOM_CONNECTIONS_KEY, Tag.TAG_COMPOUND),
			c -> fluidlogistics$phantomConnections.add(NbtUtils.readBlockPos(c)));
		fluidlogistics$phantomConnections.retainAll(connections);
	}

	@Inject(method = "removeConnectionTo", at = @At("RETURN"), remap = false)
	private void fluidlogistics$removeConnectionTo(BlockPos target, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		BlockPos localTarget = target.subtract(((ChainConveyorBlockEntity) (Object) this).getBlockPos());
		fluidlogistics$phantomConnections.remove(localTarget);
	}

	@Inject(method = "removeInvalidConnections", at = @At("RETURN"), remap = false)
	private void fluidlogistics$removeInvalidConnections(CallbackInfo ci) {
		fluidlogistics$phantomConnections.retainAll(connections);
	}

	@Inject(method = "transform", at = @At("RETURN"), remap = false)
	private void fluidlogistics$transform(BlockEntity be, StructureTransform transform, CallbackInfo ci) {
		if (fluidlogistics$phantomConnections.isEmpty()) {
			return;
		}
		Set<BlockPos> transformed = new HashSet<>();
		for (BlockPos pos : fluidlogistics$phantomConnections) {
			transformed.add(transform.applyWithoutOffset(pos));
		}
		fluidlogistics$phantomConnections.clear();
		fluidlogistics$phantomConnections.addAll(transformed);
	}

	@WrapOperation(
		method = "chainDestroyed",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;forPointsAlongChains(Lnet/minecraft/core/BlockPos;ILjava/util/function/Consumer;)Z"
		),
		remap = false
	)
	private boolean fluidlogistics$dropPhantomChainAlong(ChainConveyorBlockEntity instance, BlockPos connection,
														 int positions, Consumer<Vec3> callback,
														 Operation<Boolean> original, BlockPos target) {
		if (!fluidlogistics$phantomConnections.contains(target)) {
			return original.call(instance, connection, positions, callback);
		}
		return original.call(instance, connection, positions, (Consumer<Vec3>) vec -> instance.getLevel()
			.addFreshEntity(new ItemEntity(instance.getLevel(), vec.x, vec.y, vec.z,
				new ItemStack(AllItems.PHANTOM_CHAIN.get()))));
	}

	@ModifyExpressionValue(
		method = "chainDestroyed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;asItem()Lnet/minecraft/world/item/Item;",
			remap = true
		),
		remap = false
	)
	private Item fluidlogistics$dropPhantomChainFallback(Item original, BlockPos target) {
		if (fluidlogistics$phantomConnections.contains(target)) {
			return AllItems.PHANTOM_CHAIN.get();
		}
		return original;
	}
}

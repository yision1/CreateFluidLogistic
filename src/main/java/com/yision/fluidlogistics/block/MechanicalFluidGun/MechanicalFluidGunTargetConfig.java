package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public record MechanicalFluidGunTargetConfig(BlockPos relativePos, @Nullable Direction face) {

	public static MechanicalFluidGunTargetConfig fromAbsolute(BlockPos gunPos, BlockPos targetPos,
															  @Nullable Direction face) {
		return new MechanicalFluidGunTargetConfig(targetPos.subtract(gunPos), face);
	}

	public BlockPos absoluteFrom(BlockPos gunPos) {
		return relativePos.offset(gunPos);
	}

	public CompoundTag serialize() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("X", relativePos.getX());
		tag.putInt("Y", relativePos.getY());
		tag.putInt("Z", relativePos.getZ());
		if (face != null) {
			tag.putInt("Face", face.get3DDataValue());
		}
		return tag;
	}

	public static MechanicalFluidGunTargetConfig deserialize(CompoundTag tag) {
		BlockPos relativePos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
		Direction face = tag.contains("Face") ? Direction.from3DDataValue(tag.getInt("Face")) : null;
		return new MechanicalFluidGunTargetConfig(relativePos, face);
	}

	public MechanicalFluidGunTargetConfig transform(StructureTransform transform) {
		BlockPos transformedPos = transform.applyWithoutOffset(relativePos);
		Direction transformedFace = face;
		if (transformedFace != null) {
			transformedFace = transform.mirrorFacing(transformedFace);
			transformedFace = transform.rotateFacing(transformedFace);
		}
		return new MechanicalFluidGunTargetConfig(transformedPos, transformedFace);
	}
}

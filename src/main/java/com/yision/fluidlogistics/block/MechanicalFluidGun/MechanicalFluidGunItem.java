package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.yision.fluidlogistics.block.FluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.config.FeatureToggle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MechanicalFluidGunItem extends BlockItem {

	private static final String TAG_SELECTED_TARGETS = "SelectedTargets";
	private static final String TAG_SELECTED_DIMENSION = "SelectedDimension";

	public MechanicalFluidGunItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (MechanicalFluidGunBlock.isTargetTagged(level, pos)
			&& (player == null || !player.isShiftKeyDown())) {
			return InteractionResult.SUCCESS;
		}
		return super.useOn(context);
	}

	@Override
	protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
	                                            BlockState state) {
		boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
		if (!level.isClientSide) {
			applySelectedTargetsToPlacedGun(pos, level, stack);
			clearSelectedTargets(stack);
		}
		return updated;
	}

	@Override
	public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
		return !MechanicalFluidGunBlock.isTargetTagged(level, pos);
	}

	// --- NBT helpers for pre-bound targets on held item stack ---

	public static void addSelectedTarget(ItemStack stack, Level level, BlockPos targetPos, @Nullable Direction face) {
		CompoundTag tag = stack.getOrCreateTag();
		String dimension = level.dimension().location().toString();
		String selectedDimension = tag.contains(TAG_SELECTED_DIMENSION, Tag.TAG_STRING)
			? tag.getString(TAG_SELECTED_DIMENSION)
			: null;
		tag.putString(TAG_SELECTED_DIMENSION, dimension);

		ListTag list = dimension.equals(selectedDimension) && tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)
			? tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND)
			: new ListTag();

		// Remove existing entry for same position to avoid duplicates
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag entry = list.getCompound(i);
			if (entry.getInt("X") == targetPos.getX()
				&& entry.getInt("Y") == targetPos.getY()
				&& entry.getInt("Z") == targetPos.getZ()) {
				list.remove(i);
			}
		}

		CompoundTag entry = new CompoundTag();
		entry.putInt("X", targetPos.getX());
		entry.putInt("Y", targetPos.getY());
		entry.putInt("Z", targetPos.getZ());
		if (face != null) {
			entry.putInt("Face", face.get3DDataValue());
		}
		list.add(entry);
		tag.put(TAG_SELECTED_TARGETS, list);
	}

	public static void removeSelectedTarget(ItemStack stack, BlockPos targetPos) {
		CompoundTag tag = stack.getTag();
		if (tag == null) return;
		if (!tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)) return;

		ListTag list = tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND);
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag entry = list.getCompound(i);
			if (entry.getInt("X") == targetPos.getX()
				&& entry.getInt("Y") == targetPos.getY()
				&& entry.getInt("Z") == targetPos.getZ()) {
				list.remove(i);
			}
		}
		tag.put(TAG_SELECTED_TARGETS, list);

		if (list.isEmpty()) {
			tag.remove(TAG_SELECTED_TARGETS);
			tag.remove(TAG_SELECTED_DIMENSION);
			removeEmptyTag(stack, tag);
		}
	}

	public static List<SelectedTargetEntry> getSelectedTargets(ItemStack stack) {
		List<SelectedTargetEntry> result = new ArrayList<>();
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)) return result;

		ListTag list = tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
			Direction face = entry.contains("Face") ? Direction.from3DDataValue(entry.getInt("Face")) : null;
			result.add(new SelectedTargetEntry(pos, face));
		}
		return result;
	}

	public static List<SelectedTargetEntry> getSelectedTargets(ItemStack stack, Level level) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)) return List.of();

		String selectedDim = tag.contains(TAG_SELECTED_DIMENSION, Tag.TAG_STRING)
			? tag.getString(TAG_SELECTED_DIMENSION)
			: "";
		if (!selectedDim.equals(level.dimension().location().toString())) return List.of();

		return getSelectedTargets(stack);
	}

	public static boolean hasSelectedTargets(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)
			&& !tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND).isEmpty();
	}

	public static void clearSelectedTargets(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null) return;
		tag.remove(TAG_SELECTED_TARGETS);
		tag.remove(TAG_SELECTED_DIMENSION);
		removeEmptyTag(stack, tag);
	}

	private void applySelectedTargetsToPlacedGun(BlockPos gunPos, Level level, ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null) return;

		String selectedDim = tag.contains(TAG_SELECTED_DIMENSION) ? tag.getString(TAG_SELECTED_DIMENSION) : "";
		if (!selectedDim.equals(level.dimension().location().toString())) return;

		List<SelectedTargetEntry> rawTargets = getSelectedTargets(stack, level);
		if (rawTargets.isEmpty()) return;

		List<MechanicalFluidGunTargetConfig> validatedTargets = new ArrayList<>();
		for (SelectedTargetEntry entry : rawTargets) {
			if (!MechanicalFluidGunBlock.isSelectableTarget(level, gunPos, entry.pos)) continue;
			Direction normalizedFace = entry.face;
			BlockState targetState = level.getBlockState(entry.pos);
			Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(targetState);
			if (hatchSide != null) {
				normalizedFace = hatchSide;
			}
			validatedTargets.add(MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, entry.pos, normalizedFace));
		}

		if (!validatedTargets.isEmpty() && level.getBlockEntity(gunPos) instanceof MechanicalFluidGunBlockEntity gunBe) {
			gunBe.setTargets(validatedTargets);
		}
	}

	private static void removeEmptyTag(ItemStack stack, CompoundTag tag) {
		if (tag.isEmpty()) {
			stack.setTag(null);
		}
	}

	public record SelectedTargetEntry(BlockPos pos, @Nullable Direction face) {
	}
}

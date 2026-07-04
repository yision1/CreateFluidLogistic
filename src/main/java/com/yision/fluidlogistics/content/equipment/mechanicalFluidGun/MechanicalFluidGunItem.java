package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.network.MechanicalFluidGunPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MechanicalFluidGunItem extends BlockItem {

    private static final String TAG_SELECTED_TARGETS = "SelectedTargets";
    private static final String TAG_SELECTED_DIMENSION = "SelectedDimension";
    private static final int MAX_TARGETS = MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS;

    public record SelectedTarget(BlockPos pos, @Nullable Direction face) {
    }

    public MechanicalFluidGunItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
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

    private static void applySelectedTargetsToPlacedGun(BlockPos gunPos, Level level, ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag tag = customData.copyTag();
        String dimKey = level.dimension().location().toString();

        String selectedDim = tag.contains(TAG_SELECTED_DIMENSION, Tag.TAG_STRING)
            ? tag.getString(TAG_SELECTED_DIMENSION) : null;
        if (!dimKey.equals(selectedDim)) return;

        List<SelectedTarget> rawTargets = readTargetsFromTag(tag);
        if (rawTargets.isEmpty()) return;

        BlockEntity be = level.getBlockEntity(gunPos);
        if (!(be instanceof MechanicalFluidGunBlockEntity gunBe)) return;

        List<MechanicalFluidGunTargetConfig> validatedTargets = new ArrayList<>();
        for (SelectedTarget target : rawTargets) {
            if (validatedTargets.size() >= MAX_TARGETS) break;
            if (!MechanicalFluidGunBlock.isSelectableTarget(level, gunPos, target.pos())) continue;
            validatedTargets.add(MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, target.pos(), target.face()));
        }

        if (!validatedTargets.isEmpty()) {
            gunBe.setTargets(validatedTargets);
        }
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : new CompoundTag();
    }

    private static void writeCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void writeOrRemoveCustomTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            writeCustomTag(stack, tag);
        }
    }

    public static void addSelectedTarget(ItemStack stack, Level level, BlockPos targetPos, @Nullable Direction face) {
        CompoundTag tag = getOrCreateCustomTag(stack);

        String dimKey = level.dimension().location().toString();
        String selectedDim = tag.contains(TAG_SELECTED_DIMENSION, Tag.TAG_STRING)
            ? tag.getString(TAG_SELECTED_DIMENSION)
            : null;
        tag.putString(TAG_SELECTED_DIMENSION, dimKey);

        ListTag list = dimKey.equals(selectedDim) && tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)
            ? tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND)
            : new ListTag();

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
        writeCustomTag(stack, tag);
    }

    public static void removeSelectedTarget(ItemStack stack, BlockPos targetPos) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)) return;

        ListTag list = tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag entry = list.getCompound(i);
            if (entry.getInt("X") == targetPos.getX()
                && entry.getInt("Y") == targetPos.getY()
                && entry.getInt("Z") == targetPos.getZ()) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) {
            if (list.isEmpty()) {
                tag.remove(TAG_SELECTED_TARGETS);
                tag.remove(TAG_SELECTED_DIMENSION);
                writeOrRemoveCustomTag(stack, tag);
            } else {
                tag.put(TAG_SELECTED_TARGETS, list);
                writeCustomTag(stack, tag);
            }
        }
    }

    public static void clearSelectedTargets(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        CompoundTag tag = customData.copyTag();
        tag.remove(TAG_SELECTED_TARGETS);
        tag.remove(TAG_SELECTED_DIMENSION);
        writeOrRemoveCustomTag(stack, tag);
    }

    public static List<SelectedTarget> getSelectedTargets(ItemStack stack, Level level) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return Collections.emptyList();

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)) return Collections.emptyList();

        String dimKey = level.dimension().location().toString();
        String selectedDim = tag.contains(TAG_SELECTED_DIMENSION, Tag.TAG_STRING)
            ? tag.getString(TAG_SELECTED_DIMENSION) : null;
        if (!dimKey.equals(selectedDim)) return Collections.emptyList();

        return readTargetsFromTag(tag);
    }

    public static boolean hasSelectedTargets(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)
            && !tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND).isEmpty();
    }

    private static List<SelectedTarget> readTargetsFromTag(CompoundTag tag) {
        if (!tag.contains(TAG_SELECTED_TARGETS, Tag.TAG_LIST)) return Collections.emptyList();
        ListTag list = tag.getList(TAG_SELECTED_TARGETS, Tag.TAG_COMPOUND);
        List<SelectedTarget> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            Direction face = entry.contains("Face") ? Direction.from3DDataValue(entry.getInt("Face")) : null;
            result.add(new SelectedTarget(pos, face));
        }
        return result;
    }
}

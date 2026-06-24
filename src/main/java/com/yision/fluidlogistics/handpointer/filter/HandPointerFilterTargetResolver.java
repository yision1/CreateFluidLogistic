package com.yision.fluidlogistics.handpointer.filter;

import java.util.Optional;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import com.simibubi.create.content.redstone.link.LinkBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class HandPointerFilterTargetResolver {

    public interface ResolvedItemSlot {
        ItemStack getStack(Direction side);

        boolean setStack(Direction side, ItemStack stack);

        boolean consumesFilterItems();
    }

    private HandPointerFilterTargetResolver() {
    }

    public static Optional<HandPointerFilterTarget> resolve(Level level, Player player, BlockPos pos, BlockHitResult hitResult) {
        return resolveSlot(level, player, pos, hitResult).map(slot -> {
            BlockState state = level.getBlockState(pos);
            ItemStack iconStack = state.getBlock().asItem() != Items.AIR
                ? state.getBlock().asItem().getDefaultInstance()
                : new ItemStack(Items.BARRIER);
            return new HandPointerFilterTarget(pos, hitResult.getDirection(), hitResult.getLocation(), iconStack);
        });
    }

    public static Optional<ResolvedItemSlot> resolveSlot(Level level, Player player, BlockPos pos, BlockHitResult hitResult) {
        Optional<FilteringBehaviour> filtering = resolveBehaviour(level, player, pos, hitResult);
        if (filtering.isPresent()) {
            return filtering.map(FilteringItemSlot::new);
        }

        return resolveLinkFrequencySlot(level, player, pos, hitResult);
    }

    public static Optional<FilteringBehaviour> resolveBehaviour(Level level, Player player, BlockPos pos, BlockHitResult hitResult) {
        if (level == null || player == null || pos == null || hitResult == null) {
            return Optional.empty();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SmartBlockEntity sbe)) {
            return Optional.empty();
        }

        BlockState state = level.getBlockState(pos);
        Direction side = hitResult.getDirection();
        ItemStack heldStack = player.getMainHandItem();
        Vec3 localHit = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(pos));

        for (BlockEntityBehaviour behaviour : sbe.getAllBehaviours()) {
            if (!(behaviour instanceof FilteringBehaviour filtering)) {
                continue;
            }

            if (!(behaviour instanceof ValueSettingsBehaviour valueSettingsBehaviour)) {
                continue;
            }

            if (valueSettingsBehaviour.bypassesInput(heldStack)) {
                continue;
            }

            if (!valueSettingsBehaviour.mayInteract(player)) {
                continue;
            }

            FilteringBehaviour resolvedBehaviour = filtering;
            if (filtering instanceof SidedFilteringBehaviour sided) {
                resolvedBehaviour = sided.get(side);
                if (resolvedBehaviour == null) {
                    continue;
                }
            }

            if (!resolvedBehaviour.isActive()) {
                continue;
            }

            ValueBoxTransform positioning = resolvedBehaviour.getSlotPositioning();
            if (positioning instanceof ValueBoxTransform.Sided sidedPositioning) {
                sidedPositioning.fromSide(side);
            }

            if (!positioning.shouldRender(level, pos, state)) {
                continue;
            }

            if (!positioning.testHit(level, pos, state, localHit)) {
                continue;
            }

            return Optional.of(resolvedBehaviour);
        }

        return Optional.empty();
    }

    private static Optional<ResolvedItemSlot> resolveLinkFrequencySlot(Level level, Player player, BlockPos pos, BlockHitResult hitResult) {
        if (level == null || player == null || pos == null || hitResult == null) {
            return Optional.empty();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SmartBlockEntity sbe)) {
            return Optional.empty();
        }

        for (BlockEntityBehaviour behaviour : sbe.getAllBehaviours()) {
            if (!(behaviour instanceof LinkBehaviour link)) {
                continue;
            }

            for (boolean first : new boolean[] {false, true}) {
                if (link.testHit(first, hitResult.getLocation())) {
                    return Optional.of(new LinkFrequencySlot(link, first));
                }
            }
        }

        return Optional.empty();
    }

    private record FilteringItemSlot(FilteringBehaviour behaviour) implements ResolvedItemSlot {
        @Override
        public ItemStack getStack(Direction side) {
            return behaviour.getFilter(side).copy();
        }

        @Override
        public boolean setStack(Direction side, ItemStack stack) {
            return behaviour.setFilter(side, stack);
        }

        @Override
        public boolean consumesFilterItems() {
            return true;
        }
    }

    private record LinkFrequencySlot(LinkBehaviour behaviour, boolean first) implements ResolvedItemSlot {
        @Override
        public ItemStack getStack(Direction side) {
            return behaviour.getNetworkKey().get(first).getStack().copy();
        }

        @Override
        public boolean setStack(Direction side, ItemStack stack) {
            behaviour.setFrequency(first, stack);
            return true;
        }

        @Override
        public boolean consumesFilterItems() {
            return false;
        }
    }
}

package com.yision.fluidlogistics.content.equipment.handPointer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.yision.fluidlogistics.api.handpointer.PackagerAddress;
import com.yision.fluidlogistics.api.handpointer.PackagerAddresses.EditResult;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

@ApiStatus.Internal
public final class PackagerAddressRegistry {
    private static final PackagerAddressRegistry INSTANCE = new PackagerAddressRegistry();

    private final List<Supplier<? extends Block>> targetBlocks = new ArrayList<>();
    private Set<Block> resolvedTargetBlocks = Set.of();
    private boolean targetBlocksDirty = true;

    public static PackagerAddressRegistry instance() {
        return INSTANCE;
    }

    public static PackagerAddressRegistry create() {
        return new PackagerAddressRegistry();
    }

    public synchronized void register(Supplier<? extends Block> block) {
        Objects.requireNonNull(block, "packager address block supplier");
        if (targetBlocks.contains(block)) {
            throw new IllegalStateException("duplicate packager address block supplier");
        }
        targetBlocks.add(block);
        targetBlocksDirty = true;
    }

    public boolean isTarget(Level level, BlockPos pos) {
        return findTarget(level, pos) != null;
    }

    public EditResult set(Level level, BlockPos pos, String address) {
        Objects.requireNonNull(address, "address");
        if (address.isBlank()) {
            throw new IllegalArgumentException("packager address must not be blank");
        }

        Target target = findTarget(level, pos);
        if (target == null) {
            return EditResult.NOT_TARGET;
        }
        if (hasSignAddress(level, pos)) {
            return EditResult.SIGN_CONTROLLED;
        }
        if (isNetworkLinked(target.state())) {
            return EditResult.NETWORK_LINKED;
        }

        update(target, address, false);
        return EditResult.UPDATED;
    }

    public EditResult clear(Level level, BlockPos pos) {
        Target target = findTarget(level, pos);
        if (target == null) {
            return EditResult.NOT_TARGET;
        }
        if (isNetworkLinked(target.state())) {
            return EditResult.NETWORK_LINKED;
        }
        if (hasSignAddress(level, pos)) {
            return EditResult.SIGN_CONTROLLED;
        }
        if (target.address().clipboardAddress().isBlank()) {
            return EditResult.ALREADY_EMPTY;
        }

        update(target, "", true);
        return EditResult.UPDATED;
    }

    @Nullable
    private Target findTarget(Level level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        if (!level.isLoaded(pos)) {
            return null;
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PackagerAddress address) || !isRegistered(state.getBlock())) {
            return null;
        }
        return new Target(blockEntity, state, address);
    }

    private synchronized boolean isRegistered(Block block) {
        if (targetBlocksDirty) {
            Set<Block> resolved = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Supplier<? extends Block> supplier : targetBlocks) {
                resolved.add(Objects.requireNonNull(supplier.get(), "registered packager address block"));
            }
            resolvedTargetBlocks = resolved;
            targetBlocksDirty = false;
        }
        return resolvedTargetBlocks.contains(block);
    }

    private static boolean isNetworkLinked(BlockState state) {
        return state.hasProperty(PackagerBlock.LINKED) && state.getValue(PackagerBlock.LINKED);
    }

    private static void update(Target target, String address, boolean refreshSignAddress) {
        target.address().setClipboardAddress(address);
        if (target.blockEntity() instanceof PackagerBlockEntity packager) {
            packager.signBasedAddress = address;
            if (refreshSignAddress) {
                packager.updateSignAddress();
            }
            packager.setChanged();
            packager.notifyUpdate();
            return;
        }

        BlockEntity blockEntity = target.blockEntity();
        blockEntity.setChanged();
        Level level = blockEntity.getLevel();
        if (level != null) {
            level.sendBlockUpdated(blockEntity.getBlockPos(), target.state(), target.state(), Block.UPDATE_ALL);
        }
    }

    private static boolean hasSignAddress(Level level, BlockPos pos) {
        for (Direction direction : Iterate.directions) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                continue;
            }

            for (boolean front : Iterate.trueAndFalse) {
                SignText text = sign.getText(front);
                for (Component component : text.getMessages(false)) {
                    if (!component.getString().isBlank()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record Target(BlockEntity blockEntity, BlockState state, PackagerAddress address) {
    }
}

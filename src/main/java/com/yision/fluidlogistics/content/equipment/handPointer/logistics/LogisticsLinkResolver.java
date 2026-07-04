package com.yision.fluidlogistics.content.equipment.handPointer.logistics;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.mixin.accessor.LogisticallyLinkedBehaviourAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class LogisticsLinkResolver {

    public record ResolvedLink(
        UUID networkId,
        boolean global,
        @Nullable FactoryPanelBehaviour panel,
        @Nullable LogisticallyLinkedBehaviour behaviour
    ) {
    }

    private LogisticsLinkResolver() {
    }

    public static Optional<ResolvedLink> resolve(Level level, BlockPos pos, @Nullable BlockState state,
                                                 @Nullable Vec3 clickLocation) {
        FactoryPanelBlock.PanelSlot slot = state != null && clickLocation != null
            ? FactoryPanelBlock.getTargetedSlot(pos, state, clickLocation)
            : FactoryPanelBlock.PanelSlot.BOTTOM_LEFT;
        return resolve(level, pos, slot);
    }

    public static Optional<ResolvedLink> resolve(Level level, BlockPos pos, @Nullable FactoryPanelBlock.PanelSlot slot) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return Optional.empty();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FactoryPanelBlockEntity panel) {
            return resolvePanel(panel, slot)
                .map(fp -> new ResolvedLink(fp.network, false, fp, null));
        }

        LogisticallyLinkedBehaviour behaviour =
            BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
        if (behaviour == null) {
            return Optional.empty();
        }

        boolean global = ((LogisticallyLinkedBehaviourAccessor) behaviour).fluidlogistics$isGlobal();
        return Optional.of(new ResolvedLink(behaviour.freqId, global, null, behaviour));
    }

    public static Optional<FactoryPanelBehaviour> resolvePanel(FactoryPanelBlockEntity panel,
                                                               @Nullable FactoryPanelBlock.PanelSlot slot) {
        if (panel.panels == null || panel.panels.isEmpty()) {
            return Optional.empty();
        }

        FactoryPanelBehaviour fp = slot == null ? null : panel.panels.get(slot);
        return fp != null && fp.isActive()
            ? Optional.of(fp)
            : Optional.empty();
    }
}

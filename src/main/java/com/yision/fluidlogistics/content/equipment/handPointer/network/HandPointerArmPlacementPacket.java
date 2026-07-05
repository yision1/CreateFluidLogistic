package com.yision.fluidlogistics.content.equipment.handPointer.network;

import java.util.Collection;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.mixin.accessor.ArmBlockEntityAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerArmPlacementPacket extends SimplePacketBase {
    private final ListTag pointsTag;
    private final BlockPos armPos;

    public HandPointerArmPlacementPacket(ListTag pointsTag, BlockPos armPos) {
        this.pointsTag = pointsTag.copy();
        this.armPos = armPos;
    }

    public HandPointerArmPlacementPacket(Collection<ArmInteractionPoint> points, BlockPos armPos) {
        this(serializePoints(points, armPos), armPos);
    }

    public HandPointerArmPlacementPacket(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        this.pointsTag = tag == null ? new ListTag() : tag.getList("Points", Tag.TAG_COMPOUND);
        this.armPos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        tag.put("Points", pointsTag);
        buffer.writeNbt(tag);
        buffer.writeBlockPos(armPos);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!HandPointerInteractionGuard.canUseHandPointer(player, armPos)) {
                return;
            }

            BlockEntity blockEntity = player.level().getBlockEntity(armPos);
            if (!(blockEntity instanceof ArmBlockEntity arm)) {
                return;
            }

            ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
            accessor.setInteractionPointTag(pointsTag.copy());
            accessor.setUpdateInteractionPoints(true);
            arm.setChanged();
        });
        return true;
    }

    private static ListTag serializePoints(Collection<ArmInteractionPoint> points, BlockPos armPos) {
        ListTag tag = new ListTag();
        points.stream()
            .map(point -> point.serialize(armPos))
            .forEach(tag::add);
        return tag;
    }
}

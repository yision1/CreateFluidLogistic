package com.yision.fluidlogistics.network;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.yision.fluidlogistics.item.HandPointerItem;
import com.yision.fluidlogistics.mixin.accessor.ArmBlockEntityAccessor;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;

public record HandPointerArmPlacementPacket(ListTag pointsTag, BlockPos armPos) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerArmPlacementPacket> STREAM_CODEC =
        StreamCodec.of(
            HandPointerArmPlacementPacket::encode,
            HandPointerArmPlacementPacket::decode
        );

    public HandPointerArmPlacementPacket(Collection<ArmInteractionPoint> points, BlockPos armPos) {
        this(serializePoints(points, armPos), armPos);
    }

    public static void send(Collection<ArmInteractionPoint> points, BlockPos armPos) {
        CatnipServices.NETWORK.sendToServer(new HandPointerArmPlacementPacket(points, armPos));
    }

    private static void encode(RegistryFriendlyByteBuf buf, HandPointerArmPlacementPacket packet) {
        CompoundTag tag = new CompoundTag();
        tag.put("Points", packet.pointsTag);
        buf.writeNbt(tag);
        BlockPos.STREAM_CODEC.encode(buf, packet.armPos);
    }

    private static HandPointerArmPlacementPacket decode(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        ListTag points = tag == null ? new ListTag() : tag.getList("Points", Tag.TAG_COMPOUND);
        return new HandPointerArmPlacementPacket(points, BlockPos.STREAM_CODEC.decode(buf));
    }

    private static ListTag serializePoints(Collection<ArmInteractionPoint> points, BlockPos armPos) {
        ListTag tag = new ListTag();
        points.stream()
            .map(point -> point.serialize(armPos))
            .forEach(tag::add);
        return tag;
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.mayBuild()) {
            return;
        }

        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)) {
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(armPos)) {
            return;
        }

        if (player.distanceToSqr(armPos.getX() + 0.5D, armPos.getY() + 0.5D, armPos.getZ() + 0.5D) > 64.0D) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(armPos);
        if (!(blockEntity instanceof ArmBlockEntity arm)) {
            return;
        }

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
        accessor.setInteractionPointTag(pointsTag.copy());
        accessor.setUpdateInteractionPoints(true);
        arm.setChanged();
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_ARM_PLACEMENT;
    }
}

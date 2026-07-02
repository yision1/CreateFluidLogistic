package com.yision.fluidlogistics.content.equipment.handPointer.network;

import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import java.util.Optional;

import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterTarget;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterTargetResolver;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public record HandPointerOpenFilterMenuPacket(BlockPos pos, Direction side, Vec3 hitLocation)
    implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerOpenFilterMenuPacket> STREAM_CODEC =
        StreamCodec.of(
            HandPointerOpenFilterMenuPacket::encode,
            HandPointerOpenFilterMenuPacket::decode
        );

    private static void encode(RegistryFriendlyByteBuf buf, HandPointerOpenFilterMenuPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
        Direction.STREAM_CODEC.encode(buf, packet.side);
        buf.writeDouble(packet.hitLocation.x);
        buf.writeDouble(packet.hitLocation.y);
        buf.writeDouble(packet.hitLocation.z);
    }

    private static HandPointerOpenFilterMenuPacket decode(RegistryFriendlyByteBuf buf) {
        return new HandPointerOpenFilterMenuPacket(
            BlockPos.STREAM_CODEC.decode(buf),
            Direction.STREAM_CODEC.decode(buf),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
        );
    }

    public static void send(BlockPos pos, Direction side, Vec3 hitLocation) {
        CatnipServices.NETWORK.sendToServer(new HandPointerOpenFilterMenuPacket(pos, side, hitLocation));
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!HandPointerInteractionGuard.canUseHandPointer(player, pos)) {
            return;
        }

        BlockHitResult hitResult = new BlockHitResult(hitLocation, side, pos, false);
        Optional<HandPointerFilterTarget> target =
            HandPointerFilterTargetResolver.resolve(player.level(), player, pos, hitResult);
        if (target.isEmpty()) {
            return;
        }

        player.openMenu(target.get(), target.get()::encode);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_OPEN_FILTER_MENU;
    }
}

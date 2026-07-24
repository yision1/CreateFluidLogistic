package com.yision.fluidlogistics.content.equipment.handPointer.network;

import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner.ApplyResult;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner.Plan;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public record HandPointerCrafterConnectionPacket(
    BlockPos origin,
    BlockPos terminal,
    boolean desiredConnected,
    Direction terminalOutputDirection
) implements ServerboundPacketPayload {

    private static final int SUCCESS_COLOR = 0x9EF173;
    private static final int FAILURE_COLOR = 0xFF6171;

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerCrafterConnectionPacket> STREAM_CODEC =
        StreamCodec.of(HandPointerCrafterConnectionPacket::encode, HandPointerCrafterConnectionPacket::decode);

    public static void send(Plan plan) {
        CatnipServices.NETWORK.sendToServer(new HandPointerCrafterConnectionPacket(
            plan.geometry().origin(),
            plan.geometry().terminal(),
            plan.willConnect(),
            plan.routing().directions().get(plan.geometry().terminal())));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HandPointerCrafterConnectionPacket packet) {
        BlockPos.STREAM_CODEC.encode(buffer, packet.origin);
        BlockPos.STREAM_CODEC.encode(buffer, packet.terminal);
        buffer.writeBoolean(packet.desiredConnected);
        buffer.writeEnum(packet.terminalOutputDirection);
    }

    private static HandPointerCrafterConnectionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new HandPointerCrafterConnectionPacket(
            BlockPos.STREAM_CODEC.decode(buffer),
            BlockPos.STREAM_CODEC.decode(buffer),
            buffer.readBoolean(),
            buffer.readEnum(Direction.class));
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!HandPointerInteractionGuard.canUseHandPointer(player)) {
            sendFailure(player);
            return;
        }

        Level level = player.level();
        if (!MechanicalCrafterConnectionPlanner.isWithinSelectionRange(origin, terminal)) {
            sendFailure(player);
            return;
        }

        Plan plan = MechanicalCrafterConnectionPlanner.inspect(level, origin, terminal);
        if (!plan.valid()) {
            sendFailure(player);
            return;
        }

        ApplyResult result = MechanicalCrafterConnectionPlanner.apply(
            level, plan, desiredConnected, terminalOutputDirection);
        if (result != ApplyResult.APPLIED) {
            sendFailure(player);
            return;
        }

        level.playSound(
            null,
            terminal,
            desiredConnected ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.LEVER_CLICK,
            SoundSource.BLOCKS,
            desiredConnected ? 0.8F : 0.3F,
            desiredConnected ? 1.0F : 0.7F);
        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.crafter.updated")
            .color(SUCCESS_COLOR)
            .sendStatus(player);
    }

    private static void sendFailure(ServerPlayer player) {
        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.crafter.cannot_connect")
            .color(FAILURE_COLOR)
            .sendStatus(player);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_CRAFTER_CONNECTION;
    }
}

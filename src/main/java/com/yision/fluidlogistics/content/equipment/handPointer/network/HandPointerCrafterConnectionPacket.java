package com.yision.fluidlogistics.content.equipment.handPointer.network;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner.ApplyResult;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner.Plan;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerCrafterConnectionPacket extends SimplePacketBase {

    private static final int SUCCESS_COLOR = 0x9EF173;
    private static final int FAILURE_COLOR = 0xFF6171;

    private final BlockPos origin;
    private final BlockPos terminal;
    private final boolean desiredConnected;
    private final Direction terminalOutputDirection;

    public HandPointerCrafterConnectionPacket(BlockPos origin, BlockPos terminal,
                                              boolean desiredConnected, Direction terminalOutputDirection) {
        this.origin = origin;
        this.terminal = terminal;
        this.desiredConnected = desiredConnected;
        this.terminalOutputDirection = terminalOutputDirection;
    }

    public HandPointerCrafterConnectionPacket(FriendlyByteBuf buffer) {
        origin = buffer.readBlockPos();
        terminal = buffer.readBlockPos();
        desiredConnected = buffer.readBoolean();
        terminalOutputDirection = buffer.readEnum(Direction.class);
    }

    public static void send(Plan plan) {
        FluidLogisticsPackets.getChannel().sendToServer(new HandPointerCrafterConnectionPacket(
            plan.geometry().origin(),
            plan.geometry().terminal(),
            plan.willConnect(),
            plan.routing().directions().get(plan.geometry().terminal())));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(origin);
        buffer.writeBlockPos(terminal);
        buffer.writeBoolean(desiredConnected);
        buffer.writeEnum(terminalOutputDirection);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
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
        });
        return true;
    }

    private static void sendFailure(ServerPlayer player) {
        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.crafter.cannot_connect")
            .color(FAILURE_COLOR)
            .sendStatus(player);
    }
}

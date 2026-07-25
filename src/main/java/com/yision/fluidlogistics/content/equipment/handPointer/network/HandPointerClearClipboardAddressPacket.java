package com.yision.fluidlogistics.content.equipment.handPointer.network;

import org.joml.Vector3f;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.api.handpointer.PackagerAddresses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerClearClipboardAddressPacket extends SimplePacketBase {
    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int STATUS_NEUTRAL_COLOR = 0xA5A5A5;

    private final BlockPos pos;

    public HandPointerClearClipboardAddressPacket(BlockPos pos) {
        this.pos = pos;
    }

    public HandPointerClearClipboardAddressPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!HandPointerInteractionGuard.canUseHandPointer(player, pos)) {
                return;
            }

            Level level = player.level();

            PackagerAddresses.EditResult result = PackagerAddresses.clear(level, pos);
            switch (result) {
                case NOT_TARGET -> {
                    return;
                }
                case NETWORK_LINKED -> {
                    fluidlogistics$sendStatus(player, STATUS_INVALID_COLOR, "logistically_linked.protected");
                    fluidlogistics$sendFeedback(level, pos, false);
                    return;
                }
                case SIGN_CONTROLLED -> {
                    fluidlogistics$sendStatus(player, STATUS_INVALID_COLOR,
                        "create.fluidlogistics.hand_pointer.address_clear_blocked_by_sign");
                    fluidlogistics$sendFeedback(level, pos, false);
                    return;
                }
                case ALREADY_EMPTY -> {
                    fluidlogistics$sendStatus(player, STATUS_NEUTRAL_COLOR,
                        "create.fluidlogistics.hand_pointer.address_already_empty");
                    fluidlogistics$sendFeedback(level, pos, false);
                    return;
                }
                case UPDATED -> {
                }
            }

            fluidlogistics$sendStatus(player, STATUS_INVALID_COLOR,
                "create.fluidlogistics.hand_pointer.address_cleared");
            fluidlogistics$sendFeedback(level, pos, true);
        });
        return true;
    }

    private static void fluidlogistics$sendStatus(ServerPlayer player, int color, String key) {
        player.displayClientMessage(Component.translatable(key)
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))), true);
    }

    private static void fluidlogistics$sendFeedback(Level level, BlockPos pos, boolean success) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vector3f color = success ? new Vector3f(0.62F, 0.95F, 0.45F) : new Vector3f(1.0F, 0.38F, 0.44F);
        DustParticleOptions particle = new DustParticleOptions(color, 1.0F);
        for (int i = 0; i < 10; i++) {
            double x = pos.getX() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 0.6D;
            double y = pos.getY() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 0.6D;
            double z = pos.getZ() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 0.6D;
            serverLevel.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }

        serverLevel.playSound(null, pos,
            success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(),
            SoundSource.BLOCKS, 0.5F, success ? 1.0F : 0.85F);
    }
}

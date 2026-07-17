package com.yision.fluidlogistics.network;

import com.simibubi.create.AllBlocks;
import com.yision.fluidlogistics.api.handpointer.PackagerAddresses;
import com.yision.fluidlogistics.util.ClipboardAddressUtil;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public record ClipboardSetAddressPacket(BlockPos pos) implements ServerboundPacketPayload {
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardSetAddressPacket> STREAM_CODEC =
        StreamCodec.of(ClipboardSetAddressPacket::encode, ClipboardSetAddressPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ClipboardSetAddressPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
    }

    private static ClipboardSetAddressPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClipboardSetAddressPacket(BlockPos.STREAM_CODEC.decode(buf));
    }

    public static void send(BlockPos pos) {
        CatnipServices.NETWORK.sendToServer(new ClipboardSetAddressPacket(pos));
    }

    @Override
    public void handle(ServerPlayer player) {

        if (!player.mayBuild()) {
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return;
        }

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!AllBlocks.CLIPBOARD.isIn(heldItem)) {
            return;
        }

        String address = ClipboardAddressUtil.extractFirstAddress(heldItem);
        if (address == null) {
            if (!PackagerAddresses.isTarget(level, pos)) {
                return;
            }
            player.displayClientMessage(
                Component.translatable("create.fluidlogistics.clipboard.no_valid_address")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
                true
            );
            fluidlogistics$sendFeedback(level, pos, player, false);
            return;
        }

        PackagerAddresses.EditResult result = PackagerAddresses.set(level, pos, address);
        switch (result) {
            case NOT_TARGET, ALREADY_EMPTY -> {
                return;
            }
            case SIGN_CONTROLLED -> {
                String blockTypeName = fluidlogistics$getBlockTypeName(level.getBlockState(pos));
                player.displayClientMessage(
                    Component.translatable("create.fluidlogistics.clipboard.address_set_by_sign", blockTypeName)
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
                    true
                );
                fluidlogistics$sendFeedback(level, pos, player, false);
                return;
            }
            case NETWORK_LINKED -> {
                player.displayClientMessage(
                    Component.translatable("logistically_linked.protected")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
                    true
                );
                fluidlogistics$sendFeedback(level, pos, player, false);
                return;
            }
            case UPDATED -> {
            }
        }

        String blockTypeName = fluidlogistics$getBlockTypeName(level.getBlockState(pos));
        player.displayClientMessage(
            Component.translatable("create.fluidlogistics.clipboard.address_set", blockTypeName, address)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_CONNECTABLE_COLOR))),
            true
        );
        fluidlogistics$sendFeedback(level, pos, player, true);
    }

    private static String fluidlogistics$getBlockTypeName(BlockState state) {
        return state.getBlock().getName().getString();
    }

    private static void fluidlogistics$sendFeedback(Level level, BlockPos pos, ServerPlayer player, boolean success) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vector3f color = success ? new Vector3f(0.62F, 0.95F, 0.45F) : new Vector3f(1.0F, 0.38F, 0.44F);
        DustParticleOptions particle = new DustParticleOptions(color, 1.0F);
        for (int i = 0; i < 10; i++) {
            double x = pos.getX() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 0.6D;
            double y = pos.getY() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 0.6D;
            double z = pos.getZ() + 0.5D + (serverLevel.random.nextDouble() - 0.5D) * 0.6D;
            serverLevel.sendParticles(player, particle, true, x, y, z, 1, 0, 0, 0, 0);
        }

        if (success) {
            serverLevel.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
        } else {
            serverLevel.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.5F, 0.85F);
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.CLIPBOARD_SET_ADDRESS;
    }
}

package com.yision.fluidlogistics.network;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.yision.fluidlogistics.util.ClipboardAddressUtil;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
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

        BlockState state = level.getBlockState(pos);
        boolean isCreatePackager = AllBlocks.PACKAGER.has(state) || AllBlocks.REPACKAGER.has(state);
        boolean isFluidPackager = com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
        if (!isCreatePackager && !isFluidPackager) {
            return;
        }

        String blockTypeName = fluidlogistics$getBlockTypeName(state, level, pos);

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IPackagerOverrideData data)) {
            return;
        }

        String address = ClipboardAddressUtil.extractFirstAddress(heldItem);
        if (address == null) {
            player.displayClientMessage(
                Component.translatable("create.fluidlogistics.clipboard.no_valid_address")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
                true
            );
            fluidlogistics$sendFeedback(level, pos, player, false);
            return;
        }

        if (fluidlogistics$hasSignAddress(level, pos)) {
            player.displayClientMessage(
                Component.translatable("create.fluidlogistics.clipboard.address_set_by_sign", blockTypeName)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
                true
            );
            fluidlogistics$sendFeedback(level, pos, player, false);
            return;
        }

        boolean linkedToNetwork = state.hasProperty(PackagerBlock.LINKED) && state.getValue(PackagerBlock.LINKED);
        if (linkedToNetwork) {
            player.displayClientMessage(
                Component.translatable("logistically_linked.protected")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
                true
            );
            fluidlogistics$sendFeedback(level, pos, player, false);
            return;
        }

        data.fluidlogistics$setClipboardAddress(address);

        if (blockEntity instanceof PackagerBlockEntity packager) {
            packager.signBasedAddress = address;
            packager.setChanged();
            packager.notifyUpdate();
        }

        player.displayClientMessage(
            Component.translatable("create.fluidlogistics.clipboard.address_set", blockTypeName, address)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_CONNECTABLE_COLOR))),
            true
        );
        fluidlogistics$sendFeedback(level, pos, player, true);
    }

    private static String fluidlogistics$getBlockTypeName(BlockState state, Level level, BlockPos pos) {
        if (com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state)) {
            return Component.translatable("block.fluidlogistics.fluid_packager").getString();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RepackagerBlockEntity) {
            return Component.translatable("block.create.repackager").getString();
        }
        return Component.translatable("block.create.packager").getString();
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

    private static boolean fluidlogistics$hasSignAddress(Level level, BlockPos pos) {
        for (Direction direction : Iterate.directions) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                continue;
            }

            for (boolean front : Iterate.trueAndFalse) {
                SignText text = sign.getText(front);
                StringBuilder address = new StringBuilder();
                for (Component component : text.getMessages(false)) {
                    String line = component.getString();
                    if (!line.isBlank()) {
                        address.append(line.trim()).append(' ');
                    }
                }
                if (address.length() > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.CLIPBOARD_SET_ADDRESS;
    }
}

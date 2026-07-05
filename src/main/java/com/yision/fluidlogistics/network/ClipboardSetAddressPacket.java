package com.yision.fluidlogistics.network;

import org.joml.Vector3f;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.util.ClipboardAddressUtil;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import com.yision.fluidlogistics.util.PackagerTargetHelper;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent.Context;

public class ClipboardSetAddressPacket extends SimplePacketBase {
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    private final BlockPos pos;

    public ClipboardSetAddressPacket(BlockPos pos) {
        this.pos = pos;
    }

    public ClipboardSetAddressPacket(FriendlyByteBuf buffer) {
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
            if (player == null || !player.mayBuild()) {
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
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!PackagerTargetHelper.isClipboardAddressTarget(blockEntity, state)) {
                return;
            }

            if (!(blockEntity instanceof IPackagerOverrideData data)) {
                return;
            }

            String address = ClipboardAddressUtil.extractFirstAddress(heldItem);
            if (address == null) {
                fluidlogistics$sendStatus(player, STATUS_INVALID_COLOR, "create.fluidlogistics.clipboard.no_valid_address");
                fluidlogistics$sendFeedback(level, pos, false);
                return;
            }

            String blockTypeName = fluidlogistics$getBlockTypeName(state, level, pos);
            if (fluidlogistics$hasSignAddress(level, pos)) {
                fluidlogistics$sendStatus(player, STATUS_INVALID_COLOR,
                    Component.translatable("create.fluidlogistics.clipboard.address_set_by_sign", blockTypeName));
                fluidlogistics$sendFeedback(level, pos, false);
                return;
            }

            boolean linkedToNetwork = state.hasProperty(PackagerBlock.LINKED) && state.getValue(PackagerBlock.LINKED);
            if (linkedToNetwork) {
                fluidlogistics$sendStatus(player, STATUS_INVALID_COLOR, "logistically_linked.protected");
                fluidlogistics$sendFeedback(level, pos, false);
                return;
            }

            data.fluidlogistics$setClipboardAddress(address);

            if (blockEntity instanceof PackagerBlockEntity packager) {
                packager.signBasedAddress = address;
                packager.setChanged();
                packager.notifyUpdate();
            }

            fluidlogistics$sendStatus(player, STATUS_CONNECTABLE_COLOR,
                Component.translatable("create.fluidlogistics.clipboard.address_set", blockTypeName, address));
            fluidlogistics$sendFeedback(level, pos, true);
        });
        return true;
    }

    private static String fluidlogistics$getBlockTypeName(BlockState state, Level level, BlockPos pos) {
        if (com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state)) {
            return Component.translatable("block.fluidlogistics.fluid_packager").getString();
        }
        return Component.translatable("block.create.packager").getString();
    }

    private static void fluidlogistics$sendStatus(ServerPlayer player, int color, String key) {
        fluidlogistics$sendStatus(player, color, Component.translatable(key));
    }

    private static void fluidlogistics$sendStatus(ServerPlayer player, int color, Component component) {
        player.displayClientMessage(component.copy()
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
}

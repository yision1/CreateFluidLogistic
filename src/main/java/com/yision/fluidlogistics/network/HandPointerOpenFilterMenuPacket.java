package com.yision.fluidlogistics.network;

import java.util.Optional;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.handpointer.filter.HandPointerFilterTarget;
import com.yision.fluidlogistics.handpointer.filter.HandPointerFilterTargetResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.NetworkHooks;

public class HandPointerOpenFilterMenuPacket extends SimplePacketBase {

    private final BlockPos pos;
    private final Direction side;
    private final Vec3 hitLocation;

    public HandPointerOpenFilterMenuPacket(BlockPos pos, Direction side, Vec3 hitLocation) {
        this.pos = pos;
        this.side = side;
        this.hitLocation = hitLocation;
    }

    public HandPointerOpenFilterMenuPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.side = buffer.readEnum(Direction.class);
        this.hitLocation = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(side);
        buffer.writeDouble(hitLocation.x);
        buffer.writeDouble(hitLocation.y);
        buffer.writeDouble(hitLocation.z);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (!HandPointerInteractionGuard.canUseHandPointer(player, pos) || player.isShiftKeyDown()) {
                return;
            }

            BlockHitResult hitResult = new BlockHitResult(hitLocation, side, pos, false);
            Optional<HandPointerFilterTarget> target =
                HandPointerFilterTargetResolver.resolve(player.level(), player, pos, hitResult);
            if (target.isEmpty()) {
                return;
            }

            NetworkHooks.openScreen(player, target.get(), target.get()::encode);
        });
        return true;
    }
}

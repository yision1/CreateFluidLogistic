package com.yision.fluidlogistics.network;

import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.foundation.networking.SimplePacketBase;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class SmartFaucetDripParticlePacket extends SimplePacketBase {

    private final BlockPos faucetPos;
    private final FluidStack fluid;

    public SmartFaucetDripParticlePacket(BlockPos faucetPos, FluidStack fluid) {
        this.faucetPos = faucetPos;
        this.fluid = fluid;
    }

    public SmartFaucetDripParticlePacket(FriendlyByteBuf buffer) {
        this.faucetPos = buffer.readBlockPos();
        this.fluid = buffer.readFluidStack();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(faucetPos);
        buffer.writeFluidStack(fluid);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::handleClient));
        return true;
    }

    private void handleClient() {
        if (fluid.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }

        Vec3 spoutPos = Vec3.atCenterOf(faucetPos).add(0, -0.3, 0);
        Minecraft.getInstance().level.addParticle(FluidFX.getDrippingParticle(fluid), spoutPos.x, spoutPos.y - 0.02,
            spoutPos.z, 0.0, 0.0, 0.0);
    }
}

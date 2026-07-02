package com.yision.fluidlogistics.registry;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.particle.MechanicalFluidGunStreamParticleData;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AllFluidLogisticsParticleTypes {

    private static final DeferredRegister<ParticleType<?>> REGISTER =
        DeferredRegister.create(Registries.PARTICLE_TYPE, FluidLogistics.MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<MechanicalFluidGunStreamParticleData>>
        MECHANICAL_FLUID_GUN_STREAM = REGISTER.register("mechanical_fluid_gun_stream",
            () -> new MechanicalFluidGunStreamParticleData().createType());

    private AllFluidLogisticsParticleTypes() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerFactories(RegisterParticleProvidersEvent event) {
        new MechanicalFluidGunStreamParticleData().register(MECHANICAL_FLUID_GUN_STREAM.get(), event);
    }
}

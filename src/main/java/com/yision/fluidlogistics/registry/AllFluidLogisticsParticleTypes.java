package com.yision.fluidlogistics.registry;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.particle.MechanicalFluidGunStreamParticleData;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AllFluidLogisticsParticleTypes {
    private static final DeferredRegister<ParticleType<?>> REGISTER =
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FluidLogistics.MODID);

    public static final RegistryObject<ParticleType<MechanicalFluidGunStreamParticleData>>
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

package com.yision.fluidlogistics.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import com.simibubi.create.foundation.particle.ICustomParticleData;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.registry.AllFluidLogisticsParticleTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

@SuppressWarnings("deprecation")
public class MechanicalFluidGunStreamParticleData
    implements ParticleOptions, ICustomParticleData<MechanicalFluidGunStreamParticleData> {

    public static final int LIFETIME = 8;

    private final FluidStack fluid;

    public static final Codec<MechanicalFluidGunStreamParticleData> CODEC = RecordCodecBuilder.create(instance -> instance
        .group(FluidStack.CODEC.fieldOf("fluid")
            .forGetter(data -> data.fluid))
        .apply(instance, MechanicalFluidGunStreamParticleData::new));

    public static final ParticleOptions.Deserializer<MechanicalFluidGunStreamParticleData> DESERIALIZER =
        new ParticleOptions.Deserializer<>() {
            @Override
            public MechanicalFluidGunStreamParticleData fromCommand(
                ParticleType<MechanicalFluidGunStreamParticleData> type,
                StringReader reader) throws CommandSyntaxException {
                return new MechanicalFluidGunStreamParticleData(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1));
            }

            @Override
            public MechanicalFluidGunStreamParticleData fromNetwork(
                ParticleType<MechanicalFluidGunStreamParticleData> type,
                FriendlyByteBuf buffer) {
                return new MechanicalFluidGunStreamParticleData(buffer.readFluidStack());
            }
        };

    public MechanicalFluidGunStreamParticleData() {
        this(FluidStack.EMPTY);
    }

    public MechanicalFluidGunStreamParticleData(FluidStack fluid) {
        this.fluid = fluid.copy();
    }

    @Override
    public ParticleType<?> getType() {
        return AllFluidLogisticsParticleTypes.MECHANICAL_FLUID_GUN_STREAM.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeFluidStack(fluid);
    }

    @Override
    public String writeToString() {
        return FluidLogistics.asResource("mechanical_fluid_gun_stream") + " " + ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
    }

    @Override
    public Deserializer<MechanicalFluidGunStreamParticleData> getDeserializer() {
        return DESERIALIZER;
    }

    @Override
    public Codec<MechanicalFluidGunStreamParticleData> getCodec(ParticleType<MechanicalFluidGunStreamParticleData> type) {
        return CODEC;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ParticleProvider<MechanicalFluidGunStreamParticleData> getFactory() {
        return (data, level, x, y, z, xSpeed, ySpeed, zSpeed) ->
            new MechanicalFluidGunStreamParticle(level, data.fluid, x, y, z, xSpeed, ySpeed, zSpeed);
    }
}

class MechanicalFluidGunStreamParticle extends TextureSheetParticle {

    private final FluidStack fluid;

    MechanicalFluidGunStreamParticle(ClientLevel level, FluidStack fluid, double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.fluid = fluid.copy();

        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid.getFluid());
        setSprite(Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(clientFluid.getStillTexture(fluid)));

        gravity = 0;
        hasPhysics = false;
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        lifetime = MechanicalFluidGunStreamParticleData.LIFETIME;
        quadSize = 0.028f + level.random.nextFloat() * 0.012f;
        rCol = 0.8f;
        gCol = 0.8f;
        bCol = 0.8f;
        multiplyColor(clientFluid.getTintColor(fluid));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        move(xd, yd, zd);
        alpha = 0.75f * (1.0f - age / (float) lifetime);
    }

    @Override
    protected int getLightColor(float partialTick) {
        int brightnessForRender = super.getLightColor(partialTick);
        int skyLight = brightnessForRender >> 20;
        int blockLight = (brightnessForRender >> 4) & 0xf;
        blockLight = Math.max(blockLight, fluid.getFluid()
            .getFluidType()
            .getLightLevel(fluid));
        return (skyLight << 20) | (blockLight << 4);
    }

    private void multiplyColor(int color) {
        rCol *= (color >> 16 & 255) / 255.0f;
        gCol *= (color >> 8 & 255) / 255.0f;
        bCol *= (color & 255) / 255.0f;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
}

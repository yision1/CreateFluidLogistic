package com.yision.fluidlogistics.content.fluids.faucet;

import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.compat.createenchantmentindustry.CreateEnchantmentIndustryCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class FaucetFilling {

    private FaucetFilling() {
    }

    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        if (FillingBySpout.canItemBeFilled(level, stack)) {
            return true;
        }

        return CompatMods.createEnchantmentIndustryLoaded()
            && CreateEnchantmentIndustryCompat.canRepairItem(stack);
    }

    public static int getRequiredAmountForItem(Level level, ItemStack stack, FluidStack availableFluid) {
        int requiredAmount = FillingBySpout.getRequiredAmountForItem(level, stack, availableFluid);
        if (requiredAmount != -1) {
            return requiredAmount;
        }

        if (CompatMods.createEnchantmentIndustryLoaded()) {
            int repairAmount = CreateEnchantmentIndustryCompat.getRequiredRepairFluidAmount(level, stack, availableFluid);
            if (repairAmount > 0) {
                return repairAmount;
            }
        }

        return -1;
    }

    public static ItemStack fillItem(Level level, int requiredAmount, ItemStack stack, FluidStack availableFluid) {
        int createRequired = FillingBySpout.getRequiredAmountForItem(level, stack, availableFluid.copy());
        if (createRequired != -1) {
            return FillingBySpout.fillItem(level, requiredAmount, stack, availableFluid);
        }

        if (CompatMods.createEnchantmentIndustryLoaded()) {
            return CreateEnchantmentIndustryCompat.repairItemWithFluid(level, requiredAmount, stack, availableFluid);
        }

        return ItemStack.EMPTY;
    }

    static void writeFluid(CompoundTag tag, HolderLookup.Provider registries, String key, FluidStack stack) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(registries));
        }
    }

    static void writeItem(CompoundTag tag, HolderLookup.Provider registries, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(registries));
        }
    }

    static void writeDirection(CompoundTag tag, String key, @Nullable Direction direction) {
        if (direction != null) {
            tag.putInt(key, direction.get3DDataValue());
        }
    }

    static void writeBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    static FluidStack readFluid(CompoundTag tag, HolderLookup.Provider registries, String key) {
        return tag.contains(key) ? FluidStack.parse(registries, tag.getCompound(key)).orElse(FluidStack.EMPTY)
            : FluidStack.EMPTY;
    }

    static ItemStack readItem(CompoundTag tag, HolderLookup.Provider registries, String key) {
        return tag.contains(key) ? ItemStack.parse(registries, tag.getCompound(key)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
    }

    static @Nullable Direction readDirection(CompoundTag tag, String key) {
        return tag.contains(key) ? Direction.from3DDataValue(tag.getInt(key)) : null;
    }

    static @Nullable BlockPos readBlockPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    static void writeParticleOptions(CompoundTag tag, String key, @Nullable ParticleOptions particle) {
        if (particle == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType());
        if (id == null) {
            return;
        }
        tag.putString(key, id.toString());
    }

    static @Nullable ParticleOptions readParticleOptions(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.parse(tag.getString(key));
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (!(type instanceof SimpleParticleType simpleParticleType)) {
            return null;
        }
        return simpleParticleType;
    }
}

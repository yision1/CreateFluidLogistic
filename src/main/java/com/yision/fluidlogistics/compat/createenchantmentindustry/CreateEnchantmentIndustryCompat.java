package com.yision.fluidlogistics.compat.createenchantmentindustry;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import plus.dragons.createenchantmentindustry.common.fluids.experience.ExperienceHelper;

public final class CreateEnchantmentIndustryCompat {

    private CreateEnchantmentIndustryCompat() {
    }

    public static boolean canRepairItem(ItemStack stack) {
        return ExperienceHelper.canRepairItem(stack);
    }

    public static int getRequiredRepairFluidAmount(Level level, ItemStack stack, FluidStack availableFluid) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return -1;
        }
        if (!ExperienceHelper.canRepairItem(stack)) {
            return -1;
        }

        int availableXp = ExperienceHelper.getExperienceFromFluid(availableFluid);
        if (availableXp <= 0) {
            return -1;
        }

        int requiredXp = ExperienceHelper.repairItem(availableXp, serverLevel, stack, true);
        if (requiredXp <= 0) {
            return -1;
        }

        int requiredFluid = ExperienceHelper.getFluidFromExperience(availableFluid, requiredXp);
        return requiredFluid > 0 ? requiredFluid : -1;
    }

    public static ItemStack repairItemWithFluid(Level level, int requiredAmount, ItemStack stack, FluidStack availableFluid) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }
        if (!ExperienceHelper.canRepairItem(stack)) {
            return ItemStack.EMPTY;
        }

        FluidStack toUse = availableFluid.copyWithAmount(requiredAmount);
        int availableXp = ExperienceHelper.getExperienceFromFluid(toUse);
        if (availableXp <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = stack.copyWithCount(1);
        stack.shrink(1);
        ExperienceHelper.repairItem(availableXp, serverLevel, result, false);
        availableFluid.shrink(requiredAmount);
        return result;
    }

    public static boolean tryDropExperienceFromTank(ServerLevel level, Vec3 pos, ItemStack tankStack) {
        FluidStack fluid = CompressedTankItem.getFluid(tankStack);
        if (fluid.isEmpty()) {
            return false;
        }

        int xpPerTank = ExperienceHelper.getExperienceFromFluid(fluid);
        if (xpPerTank <= 0) {
            return false;
        }

        long totalXpLong = (long) xpPerTank * tankStack.getCount();
        int totalXp = totalXpLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalXpLong;
        ExperienceOrb.award(level, pos, totalXp);
        return true;
    }
}

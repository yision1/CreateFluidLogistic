package com.yision.fluidlogistics.registry;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.yision.fluidlogistics.FluidLogistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import org.jetbrains.annotations.Nullable;

public class FluidLogisticsArmInteractionPointTypes {
    public static final DeferredRegister<ArmInteractionPointType> ARM_INTERACTION_POINT_TYPES =
        DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, FluidLogistics.MODID);

    public static final RegistryObject<ArmInteractionPointType> FLUID_PACKAGER =
        ARM_INTERACTION_POINT_TYPES.register("fluid_packager", FluidPackagerType::new);

    public static final RegistryObject<ArmInteractionPointType> SMART_HOPPER =
        ARM_INTERACTION_POINT_TYPES.register("smart_hopper", SmartHopperType::new);

    private static class FluidPackagerType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return AllBlocks.FLUID_PACKAGER.has(state) || AllBlocks.FLUID_REPACKAGER.has(state);
        }

        @Override
        public @Nullable ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new ArmInteractionPoint(this, level, pos, state);
        }
    }

    private static class SmartHopperType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return AllBlocks.SMART_HOPPER.has(state);
        }

        @Override
        public @Nullable ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new ArmInteractionPoint(this, level, pos, state);
        }
    }
}

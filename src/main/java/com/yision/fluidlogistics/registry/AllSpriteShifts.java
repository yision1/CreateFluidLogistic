package com.yision.fluidlogistics.registry;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTType;

import static com.yision.fluidlogistics.FluidLogistics.asResource;

public class AllSpriteShifts {
    public static final CTSpriteShiftEntry
            MULTI_FLUID_TANK = getCT(AllCTTypes.RECTANGLE, "multi_fluid_tank/multi_fluid_tank"),
            MULTI_FLUID_TANK_TOP = getCT(AllCTTypes.RECTANGLE, "multi_fluid_tank/multi_fluid_tank_top"),
            MULTI_FLUID_TANK_INNER = getCT(AllCTTypes.RECTANGLE, "multi_fluid_tank/multi_fluid_tank_inner");

    private static CTSpriteShiftEntry getCT(CTType type, String blockTextureName, String connectedTextureName) {
        return CTSpriteShifter.getCT(type, asResource("block/" + blockTextureName),
                asResource("block/" + connectedTextureName + "_connected"));
    }

    private static CTSpriteShiftEntry getCT(CTType type, String blockTextureName) {
        return getCT(type, blockTextureName, blockTextureName);
    }

    public static void register() {
    }
}

package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import java.util.Arrays;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.AbstractMultiFluidTankModel;
import com.yision.fluidlogistics.registry.AllSpriteShifts;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class HorizontalMultiFluidTankModel extends AbstractMultiFluidTankModel {

    public static HorizontalMultiFluidTankModel standard(BakedModel originalModel) {
        return new HorizontalMultiFluidTankModel(originalModel, AllSpriteShifts.MULTI_FLUID_TANK, AllSpriteShifts.MULTI_FLUID_TANK_TOP,
            AllSpriteShifts.MULTI_FLUID_TANK_INNER);
    }

    private HorizontalMultiFluidTankModel(BakedModel originalModel, CTSpriteShiftEntry side, CTSpriteShiftEntry top,
                             CTSpriteShiftEntry inner) {
        super(originalModel, side, top, inner, new HorizontalMultiFluidTankCTBehaviour(side, top, inner));
    }

    @Override
    protected CullData createCullData() {
        return new CullData();
    }

    @Override
    protected boolean shouldCheck(Direction direction, BlockState state) {
        return direction.getAxis() != state.getValue(HorizontalMultiFluidTankBlock.AXIS);
    }

    public static final class CullData extends AbstractMultiFluidTankModel.CullData {
        public CullData() {
            super(6);
            Arrays.fill(culledFaces, false);
        }

        @Override
        public void setCulled(Direction face, boolean cull) {
            culledFaces[face.get3DDataValue()] = cull;
        }

        @Override
        public boolean isCulled(Direction face) {
            return culledFaces[face.get3DDataValue()];
        }
    }
}

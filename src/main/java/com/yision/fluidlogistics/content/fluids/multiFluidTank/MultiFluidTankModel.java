package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.yision.fluidlogistics.registry.AllSpriteShifts;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class MultiFluidTankModel extends AbstractMultiFluidTankModel {

    public static MultiFluidTankModel standard(BakedModel originalModel) {
        return new MultiFluidTankModel(originalModel, AllSpriteShifts.MULTI_FLUID_TANK, AllSpriteShifts.MULTI_FLUID_TANK_TOP,
                AllSpriteShifts.MULTI_FLUID_TANK_INNER);
    }

    private MultiFluidTankModel(BakedModel originalModel, CTSpriteShiftEntry side, CTSpriteShiftEntry top,
                                CTSpriteShiftEntry inner) {
        super(originalModel, side, top, inner, new MultiFluidTankCTBehaviour(side, top, inner));
    }

    @Override
    protected CullData createCullData() {
        return new VerticalCullData();
    }

    @Override
    protected boolean shouldCheck(Direction direction, BlockState state) {
        return direction.getAxis().isHorizontal();
    }

    private static final class VerticalCullData extends CullData {
        VerticalCullData() {
            super(4);
        }

        @Override
        public void setCulled(Direction face, boolean cull) {
            if (face.getAxis().isVertical()) {
                return;
            }
            culledFaces[face.get2DDataValue()] = cull;
        }

        @Override
        public boolean isCulled(Direction face) {
            if (face.getAxis().isVertical()) {
                return false;
            }
            return culledFaces[face.get2DDataValue()];
        }
    }
}

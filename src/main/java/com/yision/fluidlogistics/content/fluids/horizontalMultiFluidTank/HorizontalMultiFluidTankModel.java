package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.AbstractMultiFluidTankModel;
import com.yision.fluidlogistics.registry.AllSpriteShifts;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
        return new HorizontalCullData();
    }

    @Override
    protected boolean shouldCheck(Direction direction, BlockState state) {
        Axis axis = state.getValue(HorizontalMultiFluidTankBlock.AXIS);
        return direction.getAxis() != axis;
    }

    private static final class HorizontalCullData extends CullData {
        HorizontalCullData() {
            super(6);
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

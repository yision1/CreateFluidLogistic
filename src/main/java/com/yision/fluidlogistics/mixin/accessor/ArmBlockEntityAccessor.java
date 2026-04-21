package com.yision.fluidlogistics.mixin.accessor;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = ArmBlockEntity.class, remap = false)
public interface ArmBlockEntityAccessor {

    @Accessor("inputs")
    List<ArmInteractionPoint> getInputs();

    @Accessor("outputs")
    List<ArmInteractionPoint> getOutputs();

    @Accessor("interactionPointTag")
    void setInteractionPointTag(ListTag tag);

    @Accessor("updateInteractionPoints")
    void setUpdateInteractionPoints(boolean updateInteractionPoints);
}

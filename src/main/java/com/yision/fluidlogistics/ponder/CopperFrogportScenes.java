package com.yision.fluidlogistics.ponder;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.FrogAndConveyorScenes;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageContentHelper;
import com.yision.fluidlogistics.registry.AllBlocks;
import com.yision.fluidlogistics.registry.AllItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CopperFrogportScenes {

    public static final String PLACEMENT = "copper_frogport_placement";

    private CopperFrogportScenes() {
    }

    public static void placement(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(PLACEMENT, "Placeable Positions for Copper Frogports");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(0.85f);
        scene.setSceneOffsetY(1);

        BlockPos inputFrogport = util.grid().at(4, 2, 0);
        BlockPos outputFrogport = util.grid().at(4, 4, 4);
        BlockPos leftConveyor = util.grid().at(1, 3, 2);
        BlockPos rightConveyor = util.grid().at(7, 3, 2);
        BlockPos beltStart = util.grid().at(6, 1, 0);
        BlockPos beltEnd = util.grid().at(5, 1, 0);
        BlockPos funnel = util.grid().at(5, 2, 0);
        BlockPos depot = util.grid().at(4, 5, 4);
        Selection inputFrogportSelection = util.select().position(inputFrogport);
        Selection outputFrogportSelection = util.select().position(outputFrogport);
        Selection frogports = inputFrogportSelection.add(outputFrogportSelection);

        scene.world()
                .showSection(util.select().everywhere().substract(frogports), Direction.UP);
        scene.idle(20);

        Vec3 inputChainPoint = util.vector().of(4.498978, 3.375, 1.783035);
        Vec3 outputChainPoint = util.vector().of(4.501022, 3.375, 3.216965);
        AABB inputChainPointBox = new AABB(inputChainPoint, inputChainPoint);
        AABB outputChainPointBox = new AABB(outputChainPoint, outputChainPoint);
        ItemStack frogportItem = AllBlocks.COPPER_FROGPORT.asStack();

        scene.overlay()
                .showControls(inputChainPoint, Pointing.DOWN, 45)
                .rightClick()
                .withItem(frogportItem);
        scene.overlay()
                .chaseBoundingBoxOutline(PonderPalette.WHITE, "input_frogport_target",
                        inputChainPointBox.inflate(0.025), 45);
        scene.idle(30);

        ElementLink<WorldSectionElement> inputFrogportElement = scene.world()
                .showIndependentSection(inputFrogportSelection, Direction.EAST);
        scene.world()
                .moveSection(inputFrogportElement, util.vector().of(-1, 0, 0), 0);
        scene.world()
                .moveSection(inputFrogportElement, util.vector().of(1, 0, 0), 10);
        scene.overlay()
                .chaseBoundingBoxOutline(PonderPalette.GREEN, "input_frogport_placed",
                        inputChainPointBox.inflate(0.025), 30);
        scene.overlay()
                .showLine(PonderPalette.GREEN, util.vector().centerOf(inputFrogport), inputChainPoint, 30);
        scene.idle(35);

        scene.overlay()
                .showControls(outputChainPoint, Pointing.UP, 45)
                .rightClick()
                .withItem(frogportItem);
        scene.overlay()
                .chaseBoundingBoxOutline(PonderPalette.WHITE, "output_frogport_target",
                        outputChainPointBox.inflate(0.025), 45);
        scene.idle(30);

        ElementLink<WorldSectionElement> outputFrogportElement = scene.world()
                .showIndependentSection(outputFrogportSelection, Direction.UP);
        scene.world()
                .moveSection(outputFrogportElement, util.vector().of(0, -1, 0), 0);
        scene.world()
                .moveSection(outputFrogportElement, util.vector().of(0, 1, 0), 10);
        scene.overlay()
                .chaseBoundingBoxOutline(PonderPalette.GREEN, "output_frogport_placed",
                        outputChainPointBox.inflate(0.025), 30);
        scene.overlay()
                .showLine(PonderPalette.GREEN, util.vector().centerOf(outputFrogport), outputChainPoint, 30);
        scene.idle(35);

        scene.overlay()
                .showOutlineWithText(frogports, 70)
                .text("Copper Frogports can be placed on any face of a block")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(outputFrogport));
        scene.idle(80);

        ItemStack fluidPackage = AllItems.FLUID_PACKAGE.asStack();
        FluidPackageContentHelper.setCanonicalContents(
                fluidPackage, new FluidStack(Fluids.LAVA.getSource(), 5000));
        scene.world()
                .createItemOnBelt(beltStart, Direction.EAST, fluidPackage);
        scene.idle(22);
        scene.world()
                .removeItemsFromBelt(beltEnd);
        scene.world()
                .flapFunnel(funnel, false);
        scene.idle(10);
        scene.world()
                .modifyBlockEntity(inputFrogport, CopperFrogportBlockEntity.class,
                        frogport -> frogport.startAnimation(fluidPackage, true));

        scene.idle(70);
        scene.world()
                .modifyBlockEntity(rightConveyor, ChainConveyorBlockEntity.class,
                        blockEntity -> FrogAndConveyorScenes.boxTransfer(leftConveyor, rightConveyor, blockEntity));
        scene.idle(35);
        scene.world()
                .modifyBlockEntity(depot, DepotBlockEntity.class,
                        blockEntity -> blockEntity.setHeldItem(fluidPackage.copy()));
        scene.idle(35);
        scene.markAsFinished();
    }
}

package com.yision.fluidlogistics.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpBlockEntity;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidTransferDirection;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class FluidPumpScenes {

    public static final String PUMP_FLOW = "pump_flow";
    public static final String PUMP_SPEED = "pump_speed";

    public static void flow(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(PUMP_FLOW, "Fluid Transportation using Fluid Pumps");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos pumpPos = util.grid().at(2, 1, 1);
        Selection tank1 = util.select().fromTo(0, 2, 3, 0, 1, 3);
        Selection tank2 = util.select().fromTo(4, 2, 3, 4, 1, 3);
        Selection pipes = util.select().fromTo(3, 1, 3, 1, 1, 1);
        Selection kinetics = util.select().fromTo(4, 1, 0, 2, 1, 0);
        BlockPos leverPos = util.grid().at(4, 2, 0);
        Selection pump = util.select().position(pumpPos);

        setTankFluid(scene, util.grid().at(4, 1, 3), new FluidStack(Fluids.WATER, 9000));

        scene.world().setBlock(pumpPos, AllBlocks.FLUID_PIPE.get()
            .getAxisState(Axis.X), false);

        scene.world().showSection(tank1, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tank2, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pipes, Direction.NORTH);
        scene.idle(15);

        scene.world().destroyBlock(pumpPos);
        scene.world().restoreBlocks(pump);
        setPumpDirection(scene, pump, false);
        showPumpDirectionIcon(scene, util, pumpPos, true, 40);
        scene.world().setKineticSpeed(pump, 0);

        scene.idle(15);

        scene.overlay().showText(60)
            .text("Fluid Pumps govern the flow of their attached pipe networks")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(pumpPos));

        scene.idle(70);
        scene.world().setKineticSpeed(kinetics, -64);
        scene.world().showSection(kinetics, Direction.SOUTH);
        scene.world().showSection(util.select().position(leverPos), Direction.SOUTH);
        scene.idle(10);
        scene.world().setKineticSpeed(pump, 64);
        scene.world().propagatePipeChange(pumpPos);
        scene.effects().rotationDirectionIndicator(pumpPos.north());
        scene.idle(15);

        scene.overlay().showText(60)
            .text("Their arrow indicates the direction of flow")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(pumpPos)
                .subtract(0.5f, 0.125f, 0));

        AABB bb1 = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(.25, .25, 0)
            .move(0, 0, .25);
        AABB bb2 = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(.25, .25, 1.25);
        scene.idle(65);

        Object in = new Object();
        Object out = new Object();

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, in,
            bb1.move(util.vector().centerOf(3, 1, 3)), 3);
        scene.idle(2);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, in,
            bb2.move(util.vector().centerOf(3, 1, 2)), 50);
        scene.idle(10);

        scene.overlay().showText(50)
            .text("The network behind is now pulling fluids...")
            .attachKeyFrame()
            .placeNearTarget()
            .colored(PonderPalette.INPUT)
            .pointAt(util.vector().centerOf(3, 1, 2));

        scene.idle(60);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, out,
            bb1.move(util.vector().centerOf(1, 1, 1)
                .add(0, 0, -.5)), 3);
        scene.idle(2);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, out,
            bb2.move(util.vector().centerOf(1, 1, 2)), 50);
        scene.idle(10);

        scene.overlay().showText(50)
            .text("...while the network in front is transferring it outward")
            .placeNearTarget()
            .colored(PonderPalette.OUTPUT)
            .pointAt(util.vector().centerOf(1, 1, 2));

        scene.idle(70);
        scene.world().toggleRedstonePower(util.select().fromTo(4, 2, 0, 4, 1, 0));
        scene.effects().indicateRedstone(leverPos);
        scene.world().setKineticSpeed(kinetics, 64);
        scene.world().setKineticSpeed(pump, -64);
        scene.effects().rotationDirectionIndicator(pumpPos.north());
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(15);

        scene.overlay().showText(60)
            .text("The pump's direction is unaffected by the input rotation")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().topOf(pumpPos)
                .subtract(0.5f, 0.125f, 0));

        scene.idle(25);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, in, new AABB(leverPos.below()), 30);
        scene.idle(45);

        scene.overlay().showControls(util.vector().topOf(pumpPos), Pointing.DOWN, 40)
            .scroll();
        scene.idle(7);
        setPumpDirection(scene, pump, true);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .pointAt(util.vector().centerOf(pumpPos))
            .placeNearTarget()
            .text("Instead, scroll over the top selector to reverse the direction");
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(40);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, in,
            bb1.move(util.vector().centerOf(1, 1, 1)
                .add(0, 0, -.5)), 3);
        scene.idle(2);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, in,
            bb2.move(util.vector().centerOf(1, 1, 2)), 30);
        scene.idle(15);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, out,
            bb1.move(util.vector().centerOf(3, 1, 3)), 3);
        scene.idle(2);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, out,
            bb2.move(util.vector().centerOf(3, 1, 2)), 30);
        scene.idle(25);

        scene.markAsFinished();
    }

    public static void speed(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(PUMP_SPEED, "Throughput of Fluid Pumps");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos pumpPos = util.grid().at(2, 1, 2);
        Selection pump = util.select().position(pumpPos);
        Selection tank1 = util.select().fromTo(4, 1, 2, 4, 2, 2);
        Selection tank2 = util.select().fromTo(0, 1, 2, 0, 2, 2);
        Selection longPipe1 = util.select().fromTo(0, 4, 4, 1, 4, 2);
        Selection longPipe2 = util.select().fromTo(3, 4, 2, 4, 5, 2);

        setPumpDirection(scene, pump, false);
        setTankFluid(scene, util.grid().at(4, 1, 2), new FluidStack(Fluids.LAVA, 10000));
        scene.world().modifyBlockEntity(util.grid().at(0, 1, 2), FluidTankBlockEntity.class,
            be -> be.getTankInventory().drain(3000, FluidAction.EXECUTE));

        BlockPos east = pumpPos.east();
        scene.world().setBlock(east, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(east, AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
            .setValue(GlassFluidPipeBlock.AXIS, Axis.X), false);

        BlockPos south = pumpPos.south();
        scene.world().setBlock(south, AllBlocks.SHAFT.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Axis.Z), false);
        Selection southPump = util.select().position(south);
        Selection kinetics = util.select().fromTo(4, 1, 4, 2, 1, 4)
            .add(southPump);
        scene.world().setKineticSpeed(kinetics, 4);

        scene.world().setKineticSpeed(pump, 0);
        scene.world().showSection(pump, Direction.DOWN);
        showPumpDirectionIcon(scene, util, pumpPos, true, 40);
        scene.idle(10);
        ElementLink<WorldSectionElement> pipe1 = scene.world().showIndependentSection(longPipe1, Direction.EAST);
        scene.world().moveSection(pipe1, util.vector().of(0, -3, 0), 0);
        scene.idle(5);
        ElementLink<WorldSectionElement> pipe2 = scene.world().showIndependentSection(longPipe2, Direction.WEST);
        scene.world().moveSection(pipe2, util.vector().of(0, -3, 0), 0);
        scene.idle(15);

        scene.overlay().showText(70)
            .attachKeyFrame()
            .pointAt(util.vector().topOf(pumpPos))
            .placeNearTarget()
            .text("Regardless of speed, Fluid Pumps affect pipes connected up to 24 blocks away");
        scene.idle(75);

        scene.world().hideIndependentSection(pipe1, Direction.WEST);
        scene.idle(5);
        scene.world().hideIndependentSection(pipe2, Direction.EAST);
        scene.idle(15);

        scene.world().showSection(tank1, Direction.DOWN);
        scene.idle(2);
        scene.world().showSection(util.select().position(east), Direction.DOWN);
        scene.idle(5);
        BlockPos west = pumpPos.west();
        scene.world().showSection(util.select().position(west), Direction.DOWN);
        scene.idle(2);
        scene.world().showSection(tank2, Direction.DOWN);
        scene.idle(5);

        scene.world().showSection(kinetics, Direction.SOUTH);
        scene.idle(10);
        scene.world().setKineticSpeed(pump, 4);
        scene.effects().rotationSpeedIndicator(pumpPos);
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(40);

        scene.world().multiplyKineticSpeed(util.select().everywhere(), 8);
        scene.effects().rotationSpeedIndicator(pumpPos);
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(20);

        scene.overlay().showText(60)
            .attachKeyFrame()
            .pointAt(util.vector().topOf(pumpPos))
            .placeNearTarget()
            .text("Speeding up the input rotation changes the speed of flow propagation...");
        scene.idle(70);

        scene.overlay().showText(50)
            .pointAt(util.vector().blockSurface(util.grid().at(0, 1, 2), Direction.WEST))
            .placeNearTarget()
            .text("...and the speed at which fluids are transferred");
        scene.idle(60);

        BlockState pipeState = AllBlocks.FLUID_PIPE.getDefaultState()
            .setValue(FluidPipeBlock.DOWN, false)
            .setValue(FluidPipeBlock.UP, false);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);
        scene.idle(10);

        scene.world().setBlock(east, pipeState, true);
        scene.world().setBlock(west, pipeState, true);

        scene.world().setBlock(east.north(), pipeState.setValue(FluidPipeBlock.NORTH, false)
            .setValue(FluidPipeBlock.EAST, false), false);
        scene.world().setBlock(east.south(), pipeState.setValue(FluidPipeBlock.SOUTH, false)
            .setValue(FluidPipeBlock.EAST, false), false);
        scene.world().showSection(util.select().position(east.north()), Direction.DOWN);
        scene.world().showSection(util.select().position(east.south()), Direction.DOWN);
        Selection northPump = util.select().position(pumpPos.north());

        scene.world().setBlock(west.north(), pipeState.setValue(FluidPipeBlock.NORTH, false)
            .setValue(FluidPipeBlock.WEST, false), false);
        scene.world().setBlock(west.south(), pipeState.setValue(FluidPipeBlock.SOUTH, false)
            .setValue(FluidPipeBlock.WEST, false), false);
        scene.world().showSection(util.select().position(west.north()), Direction.DOWN);
        scene.world().showSection(util.select().position(west.south()), Direction.DOWN);

        scene.world().restoreBlocks(southPump);
        setPumpDirection(scene, southPump, false);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);
        scene.world().showSection(northPump, Direction.DOWN);
        setPumpDirection(scene, northPump, true);
        showPumpDirectionIcon(scene, util, pumpPos.north(), false, 40);
        showPumpDirectionIcon(scene, util, pumpPos.south(), true, 40);
        scene.idle(4);

        scene.world().setKineticSpeed(util.select().everywhere(), 16);
        scene.idle(20);

        scene.overlay().showOutlineWithText(util.select().fromTo(2, 1, 1, 2, 1, 3), 60)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector().topOf(pumpPos))
            .placeNearTarget()
            .text("Pumps can combine their throughputs within shared pipe networks");
        scene.idle(70);

        scene.idle(30);
        scene.overlay().showControls(util.vector().topOf(pumpPos.north()), Pointing.DOWN, 30)
            .scroll();
        scene.idle(7);
        setPumpDirection(scene, northPump, false);
        scene.idle(30);

        scene.overlay().showText(70)
            .attachKeyFrame()
            .pointAt(util.vector().topOf(pumpPos.north())
                .subtract(0.5f, 0.125f, 0))
            .placeNearTarget()
            .text("Ensure that all of them are facing in the same direction");
        scene.idle(40);

        scene.world().multiplyKineticSpeed(util.select().everywhere(), 4);
        scene.effects().rotationSpeedIndicator(pumpPos);
        scene.effects().rotationSpeedIndicator(pumpPos.north());
        scene.effects().rotationSpeedIndicator(pumpPos.south());
        scene.world().propagatePipeChange(pumpPos);
        scene.world().propagatePipeChange(pumpPos.north());
        scene.world().propagatePipeChange(pumpPos.south());
        scene.idle(100);
        scene.markAsFinished();
    }

    private static void showPumpDirectionIcon(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos,
                                              boolean positive, int duration) {
        scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, duration)
            .showing(positive ? AllIcons.I_MTD_RIGHT : AllIcons.I_MTD_LEFT);
    }

    private static void setPumpDirection(CreateSceneBuilder scene, Selection pumps, boolean positive) {
        scene.world().modifyBlockEntityNBT(pumps, FluidPumpBlockEntity.class, nbt -> {
            FluidTransferDirection direction =
                positive ? FluidTransferDirection.POSITIVE : FluidTransferDirection.NEGATIVE;
            nbt.putBoolean("DirectionManuallyConfigured", true);
            nbt.putBoolean("DefaultDirectionInitialized", true);
            nbt.putBoolean("SelectedFluidDirectionPositive", positive);
            nbt.putInt("ScrollValue", direction.ordinal());
        });
    }

    private static void setTankFluid(CreateSceneBuilder scene, BlockPos pos, FluidStack stack) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
            be -> be.getTankInventory().fill(stack, FluidAction.EXECUTE));
    }
}

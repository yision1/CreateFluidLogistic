package com.yision.fluidlogistics.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.content.logistics.fluidTransporter.FluidTransporterBlock;
import com.yision.fluidlogistics.content.logistics.fluidTransporter.FluidTransporterBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class FluidTransporterScenes {

    public static final String FLUID_TRANSPORTER = "fluid_transporter";

    public static void fluidTransporter(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(FLUID_TRANSPORTER, "Transferring Fluids with Fluid Transporters");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos pipeBottom = util.grid().at(7, 0, 3);
        BlockPos sourceTankBottom = util.grid().at(4, 1, 3);
        BlockPos sourceTankTop = util.grid().at(4, 2, 3);
        BlockPos transporter = util.grid().at(3, 1, 3);
        BlockPos targetTank = util.grid().at(1, 1, 3);

        Selection pipeRunS = util.select().fromTo(5, 1, 3, 7, 1, 3);
        Selection pipesS = pipeRunS.add(util.select().position(pipeBottom));
        Selection sourceTankS = util.select().fromTo(sourceTankBottom, sourceTankTop);
        Selection transporterS = util.select().position(transporter);
        Selection targetTankS = util.select().position(targetTank);

        scene.world().showSection(util.select().position(pipeBottom), Direction.UP);
        scene.idle(3);
        scene.world().showSection(pipeRunS, Direction.WEST);
        scene.idle(5);
        scene.world().showSection(sourceTankS, Direction.DOWN);
        scene.idle(15);
        for (int i = 0; i < 4; i++) {
            fillMultiTank(scene, sourceTankBottom, new FluidStack(Fluids.WATER, 2000));
            scene.idle(8);
        }
        scene.idle(10);
        scene.world().showSection(transporterS, Direction.EAST);
        scene.idle(8);
        ElementLink<WorldSectionElement> targetTankLink =
            scene.world().showIndependentSection(targetTankS, Direction.EAST);
        scene.world().moveSection(targetTankLink, util.vector().of(1, 0, 0), 0);
        scene.idle(20);

        scene.overlay()
            .showText(80)
            .text("Fluid Transporters can move fluid from one container to another")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(transporter));
        scene.idle(90);

        for (int i = 0; i < 3; i++) {
            drainMultiTank(scene, sourceTankBottom, new FluidStack(Fluids.WATER, 1000));
            setTransporterFluid(scene, transporterS, "minecraft:water", 1000);
            scene.idle(12);
            clearTransporterFluid(scene, transporterS);
            fillMultiTank(scene, targetTank, new FluidStack(Fluids.WATER, 1000));
            scene.idle(15);
        }
        scene.idle(25);

        for (int i = 0; i < 4; i++) {
            fillMultiTank(scene, sourceTankBottom, new FluidStack(Fluids.LAVA, 1000));
            scene.idle(8);
        }
        scene.overlay()
            .showText(80)
            .text("Fluid Transporters can use filters to restrict which fluids are allowed through")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(transporter));
        scene.idle(85);

        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        scene.overlay()
            .showFilterSlotInput(util.vector().of(3.44, 1.99, 3.5), Direction.UP, 50);
        scene.overlay()
            .showControls(util.vector().of(3.44, 1.94, 3.5), Pointing.DOWN, 50)
            .rightClick()
            .withItem(lavaBucket);
        scene.idle(10);
        scene.world().setFilterData(transporterS, FluidTransporterBlockEntity.class, lavaBucket);
        scene.idle(55);

        for (int i = 0; i < 2; i++) {
            drainMultiTank(scene, sourceTankBottom, new FluidStack(Fluids.LAVA, 1000));
            setTransporterFluid(scene, transporterS, "minecraft:lava", 1000);
            scene.idle(12);
            clearTransporterFluid(scene, transporterS);
            fillMultiTank(scene, targetTank, new FluidStack(Fluids.LAVA, 1000));
            scene.idle(15);
        }
        scene.idle(25);

        BlockPos leverPos = util.grid().at(3, 2, 3);
        Selection leverS = util.select().position(leverPos);
        scene.world().showSection(leverS, Direction.DOWN);
        scene.idle(15);

        scene.overlay()
            .showText(75)
            .text("Fluid Transporters can also be disabled by redstone signals")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(transporter));
        scene.idle(80);

        scene.world().modifyBlock(leverPos, state -> state.setValue(BlockStateProperties.POWERED, true), false);
        scene.world().modifyBlock(transporter, state -> state.setValue(FluidTransporterBlock.POWERED, true), false);
        scene.effects().indicateRedstone(transporter);
        scene.idle(80);

        scene.world().hideSection(leverS, Direction.UP);
        scene.world().modifyBlock(transporter, state -> state.setValue(FluidTransporterBlock.POWERED, false), false);
        scene.idle(5);
        scene.world().setFilterData(transporterS, FluidTransporterBlockEntity.class, ItemStack.EMPTY);
        scene.idle(25);

        BlockPos chainTransporter = util.grid().at(2, 1, 3);
        BlockPos chainInput = util.grid().at(3, 1, 3);
        BlockPos sideSmallTank = util.grid().at(3, 1, 1);
        BlockPos sideSmallTransporter = util.grid().at(3, 1, 2);
        BlockPos sideTallTransporter = util.grid().at(3, 1, 4);
        BlockPos sideTallTankBottom = util.grid().at(3, 1, 5);
        BlockPos sideTallTankTop = util.grid().at(3, 2, 5);

        Selection chainTransporterS = util.select().position(chainTransporter);
        Selection chainInputS = util.select().position(chainInput);
        Selection sideSmallTankS = util.select().position(sideSmallTank);
        Selection sideSmallTransporterS = util.select().position(sideSmallTransporter);
        Selection sideTallTransporterS = util.select().position(sideTallTransporter);
        Selection sideTallTankS = util.select().fromTo(sideTallTankBottom, sideTallTankTop);
        Selection sideTransportersS = sideTallTransporterS.add(sideSmallTransporterS);

        scene.world().moveSection(targetTankLink, util.vector().of(-1, 0, 0), 15);
        scene.idle(20);
        scene.world().showSection(chainTransporterS, Direction.SOUTH);
        scene.idle(20);

        scene.overlay()
            .showOutline(PonderPalette.GREEN, "head_to_tail", chainTransporterS.add(chainInputS), 80);
        scene.overlay()
            .showText(80)
            .text("Fluid Transporters can also connect like this")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(chainTransporter));
        scene.idle(130);

        fillMultiTank(scene, sideTallTankBottom, new FluidStack(Fluids.WATER, 4000));
        scene.world().showSection(sideSmallTankS.add(sideSmallTransporterS), Direction.SOUTH);
        scene.idle(8);
        scene.world().showSection(sideTallTankS.add(sideTallTransporterS), Direction.NORTH);
        scene.idle(20);
        scene.overlay()
            .showOutlineWithText(sideTransportersS, 90)
            .text("Note that Fluid Transporters cannot connect sideways like this")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().of(3.5, 1.5, 3));
        scene.idle(100);

        scene.markAsFinished();
    }

    private static void fillMultiTank(CreateSceneBuilder scene, BlockPos pos, FluidStack stack) {
        scene.world()
            .modifyBlockEntity(pos, MultiFluidTankBlockEntity.class,
                be -> be.getTankInventory().fill(stack, FluidAction.EXECUTE));
    }

    private static void drainMultiTank(CreateSceneBuilder scene, BlockPos pos, FluidStack stack) {
        scene.world()
            .modifyBlockEntity(pos, MultiFluidTankBlockEntity.class,
                be -> be.getTankInventory().drain(stack, FluidAction.EXECUTE));
    }

    private static void setTransporterFluid(CreateSceneBuilder scene, Selection selection, String fluidId, int amount) {
        scene.world()
            .modifyBlockEntityNBT(selection, FluidTransporterBlockEntity.class, nbt -> {
                CompoundTag tank = getPrimaryTank(nbt);
                CompoundTag fluid = new CompoundTag();
                fluid.putString("id", fluidId);
                fluid.putInt("amount", amount);
                tank.put("TankContent", fluid);
                setTankLevel(tank, amount / 1000f);
            });
    }

    private static void clearTransporterFluid(CreateSceneBuilder scene, Selection selection) {
        scene.world()
            .modifyBlockEntityNBT(selection, FluidTransporterBlockEntity.class, nbt -> {
                CompoundTag tank = getPrimaryTank(nbt);
                tank.put("TankContent", new CompoundTag());
                setTankLevel(tank, 0);
            });
    }

    private static CompoundTag getPrimaryTank(CompoundTag nbt) {
        ListTag tanks = nbt.getList("Tanks", Tag.TAG_COMPOUND);
        return tanks.getCompound(0);
    }

    private static void setTankLevel(CompoundTag tank, float level) {
        CompoundTag levelTag = tank.getCompound("Level");
        levelTag.putFloat("Value", level);
        levelTag.putFloat("Target", level);
        levelTag.putFloat("Speed", 0.25f);
        tank.put("Level", levelTag);
    }
}

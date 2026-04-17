package com.yision.fluidlogistics.ponder;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.item.CompressedTankItem;
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
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidPackagerScenes {

    public static final String FLUID_PACKAGER = "fluid_packager";
    public static final String FLUID_PACKAGER_ADDRESS = "fluid_packager_address";

    public static void fluidPackager(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(FLUID_PACKAGER, "Packaging Fluids");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();

        BlockPos packagerPos = util.grid().at(5, 2, 2);
        BlockPos tankPos = util.grid().at(5, 2, 3);
        BlockPos leverPos = util.grid().at(5, 3, 2);
        Selection packagerS = util.select().position(packagerPos);
        Selection tankS = util.select().position(tankPos);
        Selection leverS = util.select().position(leverPos);
        Selection largeCog = util.select().position(7, 0, 3);
        Selection cogNBelt = util.select().fromTo(6, 1, 2, 0, 1, 2)
                .add(util.select().position(6, 1, 3));
        Selection scaff1 = util.select().fromTo(5, 1, 3, 5, 1, 4);
        BlockPos funnelPos = util.grid().at(4, 2, 2);
        Selection funnelS = util.select().position(funnelPos);

        scene.idle(5);

        ElementLink<WorldSectionElement> tankL = scene.world()
                .showIndependentSection(tankS, Direction.DOWN);
        scene.world()
                .moveSection(tankL, util.vector().of(-2, -1, 0), 0);
        scene.idle(10);

        ElementLink<WorldSectionElement> packagerL = scene.world()
                .showIndependentSection(packagerS, Direction.SOUTH);
        scene.world()
                .moveSection(packagerL, util.vector().of(-2, -1, 0), 0);
        scene.idle(20);

        scene.overlay()
                .showText(80)
                .text("Fluid Packagers can package fluids from tanks into fluid packages")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().of(3, 1.5, 2.5));
        scene.idle(60);

        ElementLink<WorldSectionElement> leverL = scene.world()
                .showIndependentSection(leverS, Direction.DOWN);
        scene.world()
                .moveSection(leverL, util.vector().of(-2, -1, 0), 0);
        scene.idle(30);

        scene.world()
                .toggleRedstonePower(util.select().fromTo(leverPos, packagerPos));
        scene.effects()
                .indicateRedstone(leverPos.west(2).below());

        scene.idle(10);

        ItemStack fluidPackage = new ItemStack(AllItems.RARE_FLUID_PACKAGE.get());
        setFluidPackageContents(fluidPackage, new FluidStack(Fluids.WATER.getSource(), 1000));
        fluidPackagerCreate(scene, packagerPos, fluidPackage);

        scene.idle(20);

        scene.overlay()
                .showText(80)
                .text("Given redstone power, it will extract fluids from the tank and package them")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().of(3, 1.5, 2.5));
        scene.idle(90);

        scene.world()
                .moveSection(leverL, util.vector().of(2, 1, 0), 10);
        scene.world()
                .moveSection(packagerL, util.vector().of(2, 1, 0), 10);
        scene.world()
                .moveSection(tankL, util.vector().of(2, 1, 0), 10);
        scene.world()
                .showSection(scaff1, Direction.UP);
        scene.idle(10);

        scene.world()
                .showSection(largeCog, Direction.UP);
        scene.world()
                .showSection(cogNBelt, Direction.SOUTH);
        scene.idle(10);
        scene.world()
                .showSection(funnelS, Direction.DOWN);
        scene.idle(15);

        fluidPackagerClear(scene, packagerPos);
        scene.world()
                .createItemOnBelt(util.grid().at(4, 1, 2), Direction.EAST, fluidPackage);
        scene.idle(20);

        scene.world()
                .toggleRedstonePower(util.select().fromTo(5, 2, 2, 5, 3, 2));

        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 1 / 16f);

        scene.overlay()
                .showText(70)
                .text("Fluid packages can be transported and processed like regular items")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(3, 2, 2), Direction.EAST));

        scene.idle(80);

        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 16f);
        scene.idle(10);

        BlockPos packager2Pos = util.grid().at(1, 2, 3);
        BlockPos tank2Pos = util.grid().at(1, 2, 4);
        Selection packager2S = util.select().position(packager2Pos);
        Selection tank2S = util.select().position(tank2Pos);
        Selection scaff2 = util.select().fromTo(1, 1, 3, 1, 1, 4);
        BlockPos funnel2Pos = util.grid().at(1, 2, 2);
        Selection funnel2S = util.select().position(funnel2Pos);

        scene.world()
                .showSection(scaff2, Direction.DOWN);
        scene.idle(5);
        scene.world()
                .showSection(tank2S, Direction.DOWN);
        scene.world()
                .showSection(packager2S, Direction.DOWN);
        scene.idle(10);
        scene.world()
                .showSection(funnel2S, Direction.SOUTH);
        scene.rotateCameraY(-15);
        scene.idle(40);

        scene.world()
                .removeItemsFromBelt(util.grid().at(1, 1, 2));
        scene.world()
                .flapFunnel(funnel2Pos, false);
        fluidPackagerUnpack(scene, packager2Pos, fluidPackage);

        scene.idle(20);

        scene.overlay()
                .showText(90)
                .text("Inserting a fluid package will unpack it, filling the connected tank")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(packager2Pos, Direction.WEST));
        scene.idle(100);

        scene.world()
                .toggleRedstonePower(util.select().fromTo(5, 2, 2, 5, 3, 2));
        scene.effects()
                .indicateRedstone(leverPos);
        fluidPackagerCreate(scene, packagerPos, fluidPackage);
        scene.idle(25);

        fluidPackagerClear(scene, packagerPos);
        scene.world()
                .createItemOnBelt(util.grid().at(4, 1, 2), Direction.EAST, fluidPackage);
        scene.idle(30);

        scene.overlay()
                .showText(60)
                .text("Full")
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(tank2Pos));
        scene.idle(80);

        scene.overlay()
                .showOutlineWithText(util.select().fromTo(packager2Pos, tank2Pos), 90)
                .text("Packagers will not accept packages they cannot fully unpack")
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(packager2Pos, Direction.WEST));
        scene.idle(100);
    }

    private static void fluidPackagerCreate(CreateSceneBuilder scene, BlockPos pos, ItemStack box) {
        scene.world()
                .modifyBlockEntity(pos, FluidPackagerBlockEntity.class, be -> {
                    be.animationTicks = FluidPackagerBlockEntity.CYCLE;
                    be.animationInward = false;
                    be.heldBox = box;
                });
    }

    private static void fluidPackagerUnpack(CreateSceneBuilder scene, BlockPos pos, ItemStack box) {
        scene.world()
                .modifyBlockEntity(pos, FluidPackagerBlockEntity.class, be -> {
                    be.animationTicks = FluidPackagerBlockEntity.CYCLE;
                    be.animationInward = true;
                    be.previouslyUnwrapped = box;
                });
    }

    private static void fluidPackagerClear(CreateSceneBuilder scene, BlockPos pos) {
        scene.world()
                .modifyBlockEntity(pos, FluidPackagerBlockEntity.class, be -> be.heldBox = ItemStack.EMPTY);
    }

    private static void setFluidPackageContents(ItemStack packageStack, FluidStack fluid) {
        net.neoforged.neoforge.items.ItemStackHandler contents = new net.neoforged.neoforge.items.ItemStackHandler(PackageItem.SLOTS);
        ItemStack tankStack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(tankStack, fluid);
        contents.setStackInSlot(0, tankStack);
        packageStack.set(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS,
                com.simibubi.create.foundation.item.ItemHelper.containerContentsFromHandler(contents));
    }

    public static void fluidPackagerAddress(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(FLUID_PACKAGER_ADDRESS, "Routing Fluid Packages with an Address");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.875f);
        scene.showBasePlate();

        Selection frogport = util.select().position(7, 1, 1);
        Selection postbox = util.select().fromTo(6, 1, 2, 6, 2, 2);
        Selection northBelt = util.select().fromTo(3, 1, 3, 4, 1, 0);
        Selection initialKinetics = util.select().fromTo(3, 1, 5, 3, 1, 9);
        Selection largeCog = util.select().position(2, 0, 9);
        Selection saw = util.select().fromTo(2, 1, 5, 0, 1, 4);
        Selection eastBelt = util.select().fromTo(3, 1, 4, 8, 1, 4);
        Selection tunnelS = util.select().position(4, 2, 4);
        Selection tank = util.select().fromTo(7, 2, 7, 7, 2, 8);
        Selection scaffold = util.select().fromTo(7, 1, 7, 7, 1, 8);
        BlockPos packager = util.grid().at(7, 2, 6);
        Selection packagerAndLever = util.select().fromTo(7, 2, 6, 7, 3, 6);
        Selection packagerBelt = util.select().fromTo(7, 1, 6, 4, 1, 6);
        BlockPos funnel = util.grid().at(6, 2, 6);
        Selection signS = util.select().position(7, 2, 5);

        scene.idle(10);
        ElementLink<WorldSectionElement> tankL = scene.world()
                .showIndependentSection(tank, Direction.DOWN);
        scene.world()
                .moveSection(tankL, util.vector().of(-2, -1, -2), 0);
        scene.idle(5);
        scene.world()
                .showSectionAndMerge(packagerAndLever, Direction.SOUTH, tankL);
        scene.idle(20);

        scene.world()
                .showSectionAndMerge(signS, Direction.SOUTH, tankL);
        scene.idle(15);

        scene.overlay()
                .showText(40)
                .text("Warehouse")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(5, 1, 4), Direction.NORTH)
                        .add(-0.5, 0, 0));
        scene.idle(50);

        scene.overlay()
                .showText(60)
                .text("When a sign is placed on a Fluid Packager..")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(5, 1, 4), Direction.NORTH)
                        .add(-0.5, 0, 0));
        scene.idle(50);

        scene.world()
                .toggleRedstonePower(packagerAndLever);
        scene.effects()
                .indicateRedstone(util.grid().at(5, 1, 4));
        ItemStack fluidBox = new ItemStack(AllItems.RARE_FLUID_PACKAGE.get());
        setFluidPackageContents(fluidBox, new FluidStack(Fluids.WATER.getSource(), 1000));
        fluidPackagerCreate(scene, packager, fluidBox);

        scene.idle(20);
        scene.world()
                .moveSection(tankL, util.vector().of(0, 1, 0), 10);
        scene.idle(10);
        scene.world()
                .showSectionAndMerge(scaffold, Direction.NORTH, tankL);
        scene.world()
                .showSection(largeCog, Direction.UP);
        scene.world()
                .showSection(initialKinetics, Direction.NORTH);
        scene.world()
                .showSectionAndMerge(packagerBelt, Direction.SOUTH, tankL);
        scene.idle(5);
        scene.world()
                .showSectionAndMerge(util.select().position(funnel), Direction.DOWN, tankL);
        scene.idle(15);

        fluidPackagerClear(scene, packager);
        scene.world()
                .createItemOnBelt(util.grid().at(6, 1, 6), Direction.EAST, fluidBox);
        scene.idle(20);

        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 1 / 32f);
        scene.overlay()
                .showText(40)
                .text("\u2192 Warehouse")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(3, 2, 4), Direction.NORTH));
        scene.idle(50);

        scene.overlay()
                .showText(100)
                .text("Created fluid packages will carry the written lines of text as their address")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(3, 2, 4), Direction.NORTH)
                        .add(-0.5, 0, 0));
        scene.idle(120);

        scene.world()
                .hideIndependentSection(tankL, Direction.NORTH);
        scene.idle(15);
        scene.world()
                .removeItemsFromBelt(util.grid().at(5, 1, 6));
        scene.world()
                .removeItemsFromBelt(util.grid().at(4, 1, 6));
        scene.idle(15);

        scene.world()
                .showSection(eastBelt, Direction.WEST);
        scene.idle(5);
        scene.world()
                .showSection(tunnelS, Direction.DOWN);
        scene.idle(5);
        scene.world()
                .showSection(saw, Direction.EAST);
        scene.idle(5);
        scene.world()
                .showSection(northBelt, Direction.SOUTH);
        scene.rotateCameraY(-15);
        scene.idle(15);

        scene.overlay()
                .showControls(util.vector().of(4, 2.825, 4.5), Pointing.DOWN, 60)
                .withItem(com.simibubi.create.AllItems.PACKAGE_FILTER.asStack());
        scene.idle(10);
        scene.overlay()
                .showFilterSlotInput(util.vector().of(4.1, 2.825, 4.5), 50);
        scene.idle(30);

        scene.overlay()
                .showText(70)
                .text("Package filters route fluid packages based on their address")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().of(4, 2.825, 4.5));
        scene.idle(70);

        ItemStack warehouseFluidBox = new ItemStack(AllItems.RARE_FLUID_PACKAGE.get());
        setFluidPackageContents(warehouseFluidBox, new FluidStack(Fluids.WATER.getSource(), 1000));
        ItemStack factoryFluidBox = new ItemStack(AllItems.RARE_FLUID_PACKAGE.get());
        setFluidPackageContents(factoryFluidBox, new FluidStack(Fluids.LAVA.getSource(), 1000));
        PackageItem.addAddress(warehouseFluidBox, "Warehouse");
        PackageItem.addAddress(factoryFluidBox, "Factory");

        scene.world()
                .createItemOnBelt(util.grid().at(6, 1, 4), Direction.EAST, warehouseFluidBox);
        scene.idle(10);

        scene.overlay()
                .showText(50)
                .text("\u2192 Warehouse")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(7, 2, 4), Direction.WEST));
        scene.overlay()
                .showText(50)
                .colored(PonderPalette.BLUE)
                .text("Factory")
                .placeNearTarget()
                .pointAt(util.vector().of(4, 2.825, 4.5));
        scene.idle(60);

        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 32f);

        scene.idle(60);

        scene.world()
                .createItemOnBelt(util.grid().at(6, 1, 4), Direction.EAST, factoryFluidBox);
        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 1 / 32f);
        scene.idle(10);

        scene.overlay()
                .showText(50)
                .text("\u2192 Factory")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(7, 2, 4), Direction.WEST));
        scene.overlay()
                .showText(50)
                .colored(PonderPalette.BLUE)
                .text("Factory")
                .placeNearTarget()
                .pointAt(util.vector().of(4, 2.825, 4.5));
        scene.idle(60);

        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 32f);

        scene.idle(40);
        fluidPackageHopsOffBelt(scene, util.grid().at(4, 1, 0), Direction.NORTH, warehouseFluidBox);
        scene.idle(40);
        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 1 / 32f);
        scene.overlay()
                .showText(100)
                .text("For compactness, mechanical saws can unpack fluid packages straight onto a belt")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 4)));
        scene.idle(110);

        scene.world()
                .multiplyKineticSpeed(util.select().everywhere(), 32f);

        scene.idle(20);
        scene.world()
                .hideSection(eastBelt, Direction.EAST);
        scene.idle(5);
        scene.world()
                .hideSection(tunnelS, Direction.UP);
        scene.idle(5);
        scene.world()
                .hideSection(saw, Direction.WEST);
        scene.idle(5);
        scene.world()
                .hideSection(initialKinetics, Direction.UP);
        scene.world()
                .hideSection(largeCog, Direction.DOWN);
        scene.world()
                .hideSection(northBelt, Direction.NORTH);
        scene.rotateCameraY(15);
        scene.idle(15);

        ElementLink<WorldSectionElement> extrasL = scene.world()
                .showIndependentSection(postbox, Direction.DOWN);
        scene.world()
                .moveSection(extrasL, util.vector().of(-3, 0, 2), 0);
        scene.idle(5);
        scene.world()
                .showSectionAndMerge(frogport, Direction.DOWN, extrasL);
        scene.idle(20);

        scene.overlay()
                .showText(100)
                .text("Aside from filters, Frogports and Postboxes have fluid package routing abilities")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(3, 2, 4), Direction.NORTH));
        scene.idle(110);

        scene.overlay()
                .showText(80)
                .text("Inspect them to find out more about their behaviour")
                .placeNearTarget()
                .pointAt(util.vector()
                        .blockSurface(util.grid().at(3, 2, 4), Direction.NORTH));
        scene.idle(90);
    }

    private static ElementLink<net.createmod.ponder.api.element.EntityElement> fluidPackageHopsOffBelt(
            CreateSceneBuilder scene, BlockPos beltPos, Direction side, ItemStack box) {
        scene.world()
                .removeItemsFromBelt(beltPos);
        return scene.world()
                .createEntity(level -> {
                    com.simibubi.create.content.logistics.box.PackageEntity packageEntity = 
                            new com.simibubi.create.content.logistics.box.PackageEntity(
                                    level, 
                                    beltPos.getX() + 0.5 + side.getStepX() * 0.675,
                                    beltPos.getY() + 0.875, 
                                    beltPos.getZ() + 0.5 + side.getStepZ() * 0.675);
                    packageEntity.setDeltaMovement(new net.minecraft.world.phys.Vec3(
                            side.getStepX(), 1f, side.getStepZ()).scale(0.125f));
                    packageEntity.box = box;
                    return packageEntity;
                });
    }
}

package com.yision.fluidlogistics.ponder;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunTargetConfig;
import com.yision.fluidlogistics.registry.AllBlocks;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.List;

public class MechanicalFluidGunScenes {

	public static final String MECHANICAL_FLUID_GUN_SETUP = "mechanical_fluid_gun_setup";

	public static void setup(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title(MECHANICAL_FLUID_GUN_SETUP, "Setting up Mechanical Fluid Guns");
		scene.configureBasePlate(0, 0, 5);
		scene.scaleSceneView(0.9f);
		scene.showBasePlate();
		scene.idle(5);

		ItemStack gunItem = AllBlocks.MECHANICAL_FLUID_GUN.asStack();

		BlockPos gunPos = util.grid().at(2, 2, 2);
		BlockPos sourcePos = util.grid().at(2, 1, 2);
		BlockPos leftDepot = util.grid().at(0, 2, 1);
		BlockPos leftDepotSupport = util.grid().at(0, 1, 1);
		BlockPos rightDepot = util.grid().at(4, 2, 1);
		BlockPos rightDepotSupport = util.grid().at(4, 1, 1);
		BlockPos removableDepot = util.grid().at(1, 1, 0);
		BlockPos upperDepot = util.grid().at(1, 3, 4);
		BlockPos upperDepotSupport = util.grid().at(1, 2, 4);
		BlockPos upperDepotLowerSupport = util.grid().at(1, 1, 4);
		BlockPos hatch = util.grid().at(4, 2, 2);
		BlockPos hatchTank = util.grid().at(4, 2, 3);
		BlockPos hatchSupport = util.grid().at(4, 1, 3);
		BlockPos port = util.grid().at(0, 3, 3);
		BlockPos portTank = util.grid().at(0, 2, 3);
		BlockPos portSupport = util.grid().at(0, 1, 3);

		Selection gunS = util.select().position(gunPos);
		Selection sourceS = util.select().position(sourcePos);
		Selection sourceAndGunS = util.select().position(sourcePos)
			.add(util.select().position(gunPos));
		Selection initialDepotsS = util.select().position(leftDepot)
			.add(util.select().position(leftDepotSupport))
			.add(util.select().position(rightDepot))
			.add(util.select().position(rightDepotSupport));
		Selection removableDepotS = util.select().position(removableDepot);
		Selection upperDepotS = util.select().position(upperDepot)
			.add(util.select().position(upperDepotSupport))
			.add(util.select().position(upperDepotLowerSupport));
		Selection depotTargetsS = util.select().position(leftDepot)
			.add(util.select().position(leftDepotSupport))
			.add(util.select().position(rightDepot))
			.add(util.select().position(rightDepotSupport))
			.add(util.select().position(removableDepot))
			.add(util.select().position(upperDepot))
			.add(util.select().position(upperDepotSupport))
			.add(util.select().position(upperDepotLowerSupport));
		Selection kineticsS = util.select().fromTo(2, 2, 3, 2, 2, 5)
			.add(util.select().fromTo(2, 1, 5, 2, 0, 5));
		Selection gunAndKineticsS = util.select().position(gunPos)
			.add(util.select().fromTo(2, 2, 3, 2, 2, 5))
			.add(util.select().fromTo(2, 1, 5, 2, 0, 5));
		Selection plainContainersS = util.select().position(hatchTank)
			.add(util.select().position(hatchSupport))
			.add(util.select().position(portTank))
			.add(util.select().position(portSupport));
		Selection accessorsS = util.select().position(hatch)
			.add(util.select().position(port));

		AABB depotBounds = AllShapes.CASING_13PX.get(Direction.UP).bounds();
		AABB hatchBounds = AllShapes.ITEM_HATCH.get(Direction.NORTH).bounds();
		AABB fullBlockBounds = new AABB(0, 0, 0, 1, 1, 1);

		scene.world().setKineticSpeed(gunAndKineticsS, 0);
		scene.world().modifyBlockEntity(gunPos, MechanicalFluidGunBlockEntity.class,
			MechanicalFluidGunBlockEntity::clearTarget);

		ElementLink<WorldSectionElement> gunPreview =
			scene.world().showIndependentSection(gunS, Direction.DOWN);
		scene.world().moveSection(gunPreview, util.vector().of(0, -1, 0), 0);
		scene.idle(15);

		scene.effects().indicateRedstone(sourcePos);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, new Object(), fullBlockBounds.move(sourcePos), 70);
		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Mechanical Fluid Guns have to be assigned targets before they are placed")
			.pointAt(util.vector().centerOf(sourcePos))
			.placeNearTarget();
		scene.idle(80);

		scene.world().showSection(initialDepotsS, Direction.DOWN);
		scene.idle(20);

		Object leftKey = new Object();
		scene.overlay().showControls(util.vector().blockSurface(leftDepot, Direction.NORTH), Pointing.RIGHT, 45)
			.rightClick()
			.withItem(gunItem);
		scene.idle(7);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, leftKey, depotBounds.move(0, 2, 1), 280);

		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.INPUT)
			.text("Right-Click depots, belts, or other components while holding the Mechanical Fluid Gun to assign them as Targets")
			.pointAt(util.vector().blockSurface(leftDepot, Direction.WEST))
			.placeNearTarget();
		scene.idle(80);

		Object rightKey = new Object();
		scene.overlay().showControls(util.vector().blockSurface(rightDepot, Direction.NORTH), Pointing.RIGHT, 30)
			.rightClick()
			.withItem(gunItem);
		scene.idle(7);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, rightKey, depotBounds.move(4, 2, 1), 180);
		scene.idle(45);

		scene.world().showSection(removableDepotS, Direction.DOWN);
		scene.idle(15);

		Object removeKey = new Object();
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, removeKey, depotBounds.move(1, 1, 0), 55);

		scene.overlay().showText(55)
			.colored(PonderPalette.WHITE)
			.text("Right-Click a selected target again to remove its Selection")
			.pointAt(util.vector().blockSurface(removableDepot, Direction.WEST))
			.placeNearTarget();
		scene.idle(35);

		scene.overlay().showControls(util.vector().blockSurface(removableDepot, Direction.NORTH), Pointing.RIGHT, 25)
			.rightClick()
			.withItem(gunItem);
		scene.idle(25);

		scene.world().moveSection(gunPreview, util.vector().of(0, 1, 0), 20);
		scene.idle(20);
		scene.world().hideIndependentSection(gunPreview, null);
		scene.world().showSection(sourceAndGunS, null);
		scene.idle(10);

		scene.world().modifyBlockEntity(gunPos, MechanicalFluidGunBlockEntity.class, be -> be.setTargets(List.of(
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, leftDepot, Direction.UP),
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, rightDepot, Direction.UP)
		)));

		scene.overlay().showText(70)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Once placed, the Mechanical Fluid Gun will target the blocks selected previously")
			.pointAt(util.vector().blockSurface(gunPos, Direction.WEST).add(0.5, 1.5, 0))
			.placeNearTarget();
		scene.idle(80);

		scene.overlay().showOutlineWithText(sourceS, 80)
			.attachKeyFrame()
			.text("Mechanical Fluid Guns use the fluid container below them as their fluid source")
			.pointAt(util.vector().centerOf(sourcePos))
			.placeNearTarget();
		scene.idle(90);

		scene.world().showSection(kineticsS, Direction.DOWN);
		scene.idle(10);
		scene.world().setKineticSpeed(gunAndKineticsS, -48);
		scene.effects().indicateSuccess(gunPos);
		scene.idle(20);

		CompoundTag water = waterTag();
		scene.world().createItemOnBeltLike(leftDepot, Direction.SOUTH, new ItemStack(Items.BUCKET));
		scene.idle(10);
		startSpray(scene, gunS, 0, water);
		scene.idle(25);
		stopSpray(scene, gunS);
		scene.world().removeItemsFromBelt(leftDepot);
		scene.world().createItemOnBeltLike(leftDepot, Direction.UP, new ItemStack(Items.WATER_BUCKET));
		scene.idle(25);

		scene.world().showSection(upperDepotS, Direction.DOWN);
		scene.idle(10);
		scene.world().modifyBlockEntity(gunPos, MechanicalFluidGunBlockEntity.class, be -> be.setTargets(List.of(
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, leftDepot, Direction.UP),
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, rightDepot, Direction.UP),
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, upperDepot, Direction.UP)
		)));

		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, new Object(), depotBounds.move(0, 2, 1), 60);
		scene.idle(5);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, new Object(), depotBounds.move(4, 2, 1), 60);
		scene.idle(5);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, new Object(), depotBounds.move(1, 3, 4), 60);
		scene.idle(5);
		scene.overlay().showText(80)
			.attachKeyFrame()
			.text("They can have any amount of targets within their range")
			.pointAt(util.vector().blockSurface(upperDepot, Direction.WEST))
			.placeNearTarget();
		scene.idle(70);

		scene.world().createItemOnBeltLike(upperDepot, Direction.SOUTH, new ItemStack(Items.BUCKET));
		scene.idle(10);
		startSpray(scene, gunS, 2, water);
		scene.idle(25);
		scene.world().removeItemsFromBelt(upperDepot);
		scene.world().createItemOnBeltLike(upperDepot, Direction.UP, new ItemStack(Items.WATER_BUCKET));
		scene.idle(10);
		stopSpray(scene, gunS);
		scene.idle(35);
		scene.idle(20);

		scene.world().removeItemsFromBelt(leftDepot);
		scene.world().removeItemsFromBelt(upperDepot);
		scene.world().hideSection(depotTargetsS, Direction.UP);
		scene.idle(15);
		scene.world().showSection(plainContainersS, Direction.DOWN);
		scene.idle(20);
		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Not every fluid container can be interacted with directly")
			.pointAt(util.vector().centerOf(hatchTank))
			.placeNearTarget();
		scene.idle(90);

		scene.world().showSection(accessorsS, Direction.DOWN);
		scene.idle(15);
		Object hatchKey = new Object();
		Object portKey = new Object();
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, hatchKey, hatchBounds.move(4, 2, 2), 80);
		scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, portKey, fullBlockBounds.move(0, 3, 3), 80);
		scene.overlay().showText(80)
			.attachKeyFrame()
			.colored(PonderPalette.INPUT)
			.text("In this situation, Fluid Hatches and Multi-Fluid Access Ports can expose them as targets")
			.pointAt(util.vector().of(4.5, 2.5, 2.12))
			.placeNearTarget();
		scene.idle(80);

		scene.world().modifyBlockEntity(gunPos, MechanicalFluidGunBlockEntity.class, be -> be.setTargets(List.of(
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, hatch, Direction.NORTH),
			MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, port, Direction.EAST)
		)));
		startSpray(scene, gunS, 0, water);
		scene.idle(30);
		fillCreateTank(scene, hatchTank, 1000);
		scene.idle(10);
		stopSpray(scene, gunS);
		scene.idle(30);

		scene.markAsFinished();
	}

	private static CompoundTag waterTag() {
		CompoundTag water = new CompoundTag();
		water.putString("id", "minecraft:water");
		water.putInt("amount", 250);
		return water;
	}

	private static void startSpray(CreateSceneBuilder scene, Selection gunS, int targetIndex, CompoundTag fluid) {
		scene.world().modifyBlockEntityNBT(gunS, MechanicalFluidGunBlockEntity.class, nbt -> {
			nbt.putInt("ActiveTargetIndex", targetIndex);
			nbt.putBoolean("WorkCycleActive", true);
			nbt.putFloat("TargetProgress", 0.75f);
			nbt.putBoolean("Spraying", true);
			nbt.putInt("SprayTicks", 20);
			nbt.put("RenderingFluid", fluid.copy());
		});
	}

	private static void stopSpray(CreateSceneBuilder scene, Selection gunS) {
		scene.world().modifyBlockEntityNBT(gunS, MechanicalFluidGunBlockEntity.class, nbt -> {
			nbt.remove("RenderingFluid");
			nbt.putBoolean("Spraying", false);
			nbt.putInt("SprayTicks", 0);
			nbt.putBoolean("WorkCycleActive", false);
			nbt.putFloat("TargetProgress", 1);
			nbt.putInt("ActiveTargetIndex", -1);
			nbt.putBoolean("IsFillingItem", false);
			nbt.putInt("ProcessingTicks", 0);
			nbt.putInt("ProcessingTarget", 0);
			nbt.remove("ProcessingBeltPos");
			nbt.remove("ProcessingBeltAimX");
			nbt.remove("ProcessingBeltAimY");
			nbt.remove("ProcessingBeltAimZ");
		});
	}

	private static void fillCreateTank(CreateSceneBuilder scene, BlockPos tankPos, int amount) {
		scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class,
			be -> be.getTankInventory().fill(new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE));
	}
}

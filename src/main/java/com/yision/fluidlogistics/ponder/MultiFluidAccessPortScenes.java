package com.yision.fluidlogistics.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlock;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class MultiFluidAccessPortScenes {

	public static final String MULTI_FLUID_ACCESS_PORT = "multi_fluid_access_port";

	public static void multiFluidAccessPort(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title(MULTI_FLUID_ACCESS_PORT, "Accessing Fluids with Multi Fluid Access Ports");
		scene.configureBasePlate(0, 0, 7);
		scene.scaleSceneView(0.9f);
		scene.showBasePlate();
		scene.idle(5);

		BlockPos mftankBottom = util.grid().at(5, 1, 3);
		BlockPos portPos = util.grid().at(4, 1, 3);
		BlockPos transNorth = util.grid().at(4, 1, 2);
		BlockPos tankNorth = util.grid().at(4, 1, 1);
		BlockPos transWest = util.grid().at(3, 1, 3);
		BlockPos tankWestBottom = util.grid().at(2, 1, 3);
		BlockPos transSouth = util.grid().at(4, 1, 4);
		BlockPos tankSouthBottom = util.grid().at(4, 1, 5);

		Selection mftankS = util.select().fromTo(5, 1, 3, 5, 2, 3);
		Selection portS = util.select().position(portPos);
		Selection transNorthS = util.select().position(transNorth);
		Selection tankNorthS = util.select().position(tankNorth);
		Selection transWestS = util.select().position(transWest);
		Selection tankWestS = util.select().fromTo(2, 1, 3, 2, 1, 3);
		Selection transSouthS = util.select().position(transSouth);
		Selection tankSouthS = util.select().fromTo(4, 1, 5, 4, 2, 5);
		Selection pipesS = util.select().fromTo(6, 1, 3, 7, 1, 3);

		ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
		ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);

		scene.world()
			.showSection(util.select()
				.position(7, 0, 3), Direction.UP);
		scene.idle(3);
		scene.world()
			.showSection(pipesS, Direction.WEST);
		scene.idle(5);
		scene.world()
			.showSection(mftankS, Direction.DOWN);
		scene.idle(20);

		for (int i = 0; i < 4; i++) {
			scene.world()
				.modifyBlockEntity(mftankBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
			scene.idle(5);
		}
		for (int i = 0; i < 4; i++) {
			scene.world()
				.modifyBlockEntity(mftankBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
			scene.idle(5);
		}
		scene.idle(10);

		scene.overlay()
			.showText(100)
			.text("Accessing multi-fluid containers can sometimes be troublesome")
			.placeNearTarget()
			.pointAt(util.vector()
				.topOf(mftankBottom));
		scene.idle(110);

		scene.world()
			.showSection(portS, Direction.EAST);
		scene.idle(20);

		scene.overlay()
			.showText(100)
			.text("Multi Fluid Access Ports can extend fluid containers for easier access")
			.placeNearTarget()
			.pointAt(util.vector()
				.blockSurface(portPos, Direction.WEST));
		scene.idle(110);

		scene.world()
			.showSection(transNorthS, Direction.SOUTH);
		scene.world()
			.showSection(tankNorthS, Direction.SOUTH);
		scene.idle(5);
		scene.world()
			.showSection(transWestS, Direction.EAST);
		scene.world()
			.showSection(tankWestS, Direction.EAST);
		scene.idle(5);
		scene.world()
			.showSection(transSouthS, Direction.NORTH);
		scene.world()
			.showSection(tankSouthS, Direction.NORTH);
		scene.idle(20);

		scene.overlay()
			.showOutline(PonderPalette.GREEN, "left", transNorthS.add(tankNorthS), 80);
		scene.overlay()
			.showOutline(PonderPalette.BLUE, "back", transWestS.add(tankWestS), 80);
		scene.overlay()
			.showOutline(PonderPalette.OUTPUT,  "right", transSouthS.add(tankSouthS), 80);
		scene.overlay()
			.showText(80)
			.text("Multi Fluid Access Ports can access fluids in up to three directions at once")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.topOf(portPos));
		scene.idle(90);

		scene.overlay()
			.showText(80)
			.text("Filters can also be configured separately for each of the three directions")
			.placeNearTarget()
			.pointAt(util.vector()
				.topOf(portPos));
		scene.idle(90);

		scene.overlay()
			.showFilterSlotInput(util.vector()
				.of(4.5, 2.01, 3.19), Direction.UP, 40);
		scene.overlay()
			.showControls(util.vector()
				.of(4.5, 1.98, 3.19), Pointing.DOWN, 40)
			.rightClick()
			.withItem(waterBucket);
		scene.idle(10);
		setPortFilter(scene, portS, "FilteringLeft", waterBucket);
		scene.idle(60);

		scene.overlay()
			.showFilterSlotInput(util.vector()
				.of(4.19, 2.01, 3.5), Direction.UP, 40);
		scene.overlay()
			.showControls(util.vector()
				.of(4.19, 1.98, 3.5), Pointing.DOWN, 40)
			.rightClick()
			.withItem(lavaBucket);
		scene.idle(10);
		setPortFilter(scene, portS, "FilteringBack", lavaBucket);
		scene.idle(60);

		for (int i = 0; i < 4; i++) {
			scene.world()
				.modifyBlockEntity(mftankBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
			scene.world()
				.modifyBlockEntity(tankNorth, FluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
			scene.world()
				.modifyBlockEntity(tankSouthBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
			scene.world()
				.modifyBlockEntity(tankSouthBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
			scene.world()
				.modifyBlockEntity(mftankBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.drain(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
			scene.world()
				.modifyBlockEntity(tankWestBottom, FluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
			scene.idle(5);
		}
		scene.idle(40);

		BlockPos leverPos = util.grid().at(4, 2, 3);
		Selection leverS = util.select().position(leverPos);

		scene.world()
			.setBlock(leverPos, Blocks.LEVER.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
				.setValue(BlockStateProperties.POWERED, false), false);
		scene.world()
			.showSection(leverS, Direction.DOWN);
		scene.idle(20);

		scene.overlay()
			.showText(80)
			.text("Multi Fluid Access Ports can also be disabled by redstone signals")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(leverPos));
		scene.idle(90);

		scene.world()
			.modifyBlock(leverPos, state -> state.cycle(BlockStateProperties.POWERED), false);
		scene.world()
			.modifyBlock(portPos,
				state -> state.setValue(MultiFluidAccessPortBlock.ATTACHED, false), false);
		scene.idle(15);

		for (int i = 0; i < 4; i++) {
			scene.world()
				.modifyBlockEntity(mftankBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
			scene.idle(5);
		}
		for (int i = 0; i < 4; i++) {
			scene.world()
				.modifyBlockEntity(mftankBottom, MultiFluidTankBlockEntity.class,
					be -> be.getTankInventory()
						.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
			scene.idle(5);
		}
		scene.idle(20);

		scene.world()
			.hideSection(leverS, Direction.UP);
		scene.idle(3);
		scene.world()
			.setBlock(leverPos, Blocks.AIR.defaultBlockState(), false);
		scene.world()
			.hideSection(transNorthS.add(tankNorthS), Direction.NORTH);
		scene.world()
			.hideSection(transWestS.add(tankWestS), Direction.WEST);
		scene.world()
			.hideSection(transSouthS.add(tankSouthS), Direction.SOUTH);
		scene.idle(10);
		scene.world()
			.modifyBlock(portPos,
				state -> state.setValue(MultiFluidAccessPortBlock.ATTACHED, true), false);
		scene.idle(10);

		BlockPos chainPortPos = util.grid().at(3, 1, 3);
		Selection chainPortS = util.select().position(chainPortPos);

		scene.world()
			.setBlock(chainPortPos, AllBlocks.MULTI_FLUID_ACCESS_PORT.getDefaultState()
				.setValue(MultiFluidAccessPortBlock.FACING, Direction.EAST)
				.setValue(MultiFluidAccessPortBlock.TARGET, AttachFace.WALL)
				.setValue(MultiFluidAccessPortBlock.ATTACHED, false), false);
		scene.world()
			.showSection(chainPortS, Direction.EAST);
		scene.idle(20);

		scene.overlay()
			.showOutlineWithText(chainPortS.add(portS), 80)
			.text("Note that Multi Fluid Access Ports cannot be chained")
			.colored(PonderPalette.RED)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(chainPortPos));
		scene.idle(90);

		BlockPos topPortPos = util.grid().at(5, 3, 3);
		Selection topPortS = util.select().position(topPortPos);

		scene.world()
			.setBlock(topPortPos, AllBlocks.MULTI_FLUID_ACCESS_PORT.getDefaultState()
				.setValue(MultiFluidAccessPortBlock.FACING, Direction.NORTH)
				.setValue(MultiFluidAccessPortBlock.TARGET, AttachFace.FLOOR)
				.setValue(MultiFluidAccessPortBlock.ATTACHED, true), false);
		scene.world()
			.showSection(topPortS, Direction.DOWN);
		scene.idle(20);

		scene.overlay()
			.showOutline(PonderPalette.OUTPUT, "multiple_ports", topPortS.add(portS), 90);
		scene.overlay()
			.showText(90)
			.text("However, one fluid container can connect to multiple Multi Fluid Access Ports at the same time")
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(topPortPos));
		scene.idle(100);

		scene.markAsFinished();
	}

	private static void setPortFilter(CreateSceneBuilder scene, Selection selection, String nbtKey,
		ItemStack filter) {
		scene.world()
			.modifyBlockEntityNBT(selection, MultiFluidAccessPortBlockEntity.class, nbt -> {
				CompoundTag filterTag = new CompoundTag();
				filterTag.put("Filter", filter.saveOptional(scene.world()
					.getHolderLookupProvider()));
				filterTag.putInt("FilterAmount", 64);
				filterTag.putBoolean("UpTo", true);
				nbt.put(nbtKey, filterTag);
			});
	}
}

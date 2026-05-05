package com.yision.fluidlogistics.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperBlock;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperBlockEntity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class SmartHopperScenes {

	public static final String SMART_HOPPER = "smart_hopper";

	public static void smartHopper(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title(SMART_HOPPER, "Transferring Items and Fluids with Smart Hoppers");
		scene.configureBasePlate(0, 0, 7);
		scene.scaleSceneView(0.9f);
		scene.showBasePlate();
		scene.idle(5);

		BlockPos itemOutputChest = util.grid()
			.at(2, 1, 4);
		BlockPos itemHopper = util.grid()
			.at(3, 1, 4);
		BlockPos itemInputChest = util.grid()
			.at(3, 2, 4);
		BlockPos funnel = util.grid()
			.at(1, 1, 4);
		BlockPos itemOutputChestView = itemOutputChest.offset(1, 0, -1);
		BlockPos itemHopperView = itemHopper.offset(1, 0, -1);
		BlockPos itemInputChestView = itemInputChest.offset(1, 0, -1);
		BlockPos funnelView = funnel.offset(1, 0, -1);

		BlockPos outputTank = util.grid()
			.at(2, 1, 3);
		BlockPos outputHopper = util.grid()
			.at(3, 1, 3);
		BlockPos inputHopper = util.grid()
			.at(4, 1, 3);
		BlockPos inputTank = util.grid()
			.at(4, 2, 3);
		BlockPos leverPos = util.grid()
			.at(3, 2, 3);

		Selection itemOutputChestS = util.select()
			.position(itemOutputChest);
		Selection itemHopperS = util.select()
			.position(itemHopper);
		Selection itemInputChestS = util.select()
			.position(itemInputChest);
		Selection funnelS = util.select()
			.position(funnel);
		Selection itemOutputChestViewS = util.select()
			.position(itemOutputChestView);
		Selection itemHopperViewS = util.select()
			.position(itemHopperView);
		Selection itemInputChestViewS = util.select()
			.position(itemInputChestView);
		Selection funnelViewS = util.select()
			.position(funnelView);
		Selection outputTankS = util.select()
			.position(outputTank);
		Selection outputHopperS = util.select()
			.position(outputHopper);
		Selection inputHopperS = util.select()
			.position(inputHopper);
		Selection inputTankS = util.select()
			.position(inputTank);
		Selection leverS = util.select()
			.position(leverPos);

		ItemStack copper = new ItemStack(Items.COPPER_INGOT, 8);
		ItemStack gold = new ItemStack(Items.GOLD_INGOT, 8);
		ItemStack iron = new ItemStack(Items.IRON_INGOT, 8);

		fillTank(scene, inputTank, 1000);
		scene.world()
			.showSection(inputHopperS.add(inputTankS), Direction.DOWN);
		ElementLink<WorldSectionElement> firstOutputTank =
			scene.world()
				.showIndependentSection(outputTankS, Direction.EAST);
		scene.world()
			.moveSection(firstOutputTank, util.vector()
				.of(1, 0, 0), 0);
		scene.idle(15);

		scene.overlay()
			.showText(80)
			.text("Smart Hoppers pull fluids from above and output them toward the spout")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(inputHopper));
		scene.idle(90);

		fillTank(scene, outputTank, 250);
		scene.idle(35);

		scene.overlay()
			.showText(70)
			.text("Smart Hoppers can also be disabled by redstone signals")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(inputHopper));
		scene.idle(80);
		scene.world()
			.setBlock(leverPos, Blocks.LEVER.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
				.setValue(BlockStateProperties.POWERED, false), false);
		scene.world()
			.showSection(leverS, Direction.EAST);
		scene.idle(15);
		scene.world()
			.modifyBlock(leverPos, state -> state.setValue(BlockStateProperties.POWERED, true), false);
		scene.world()
			.modifyBlock(inputHopper, state -> state.setValue(SmartHopperBlock.POWERED, true), false);
		scene.effects()
			.indicateRedstone(inputHopper);
		scene.idle(70);
		scene.world()
			.hideSection(leverS, Direction.UP);
		scene.world()
			.modifyBlock(inputHopper, state -> state.setValue(SmartHopperBlock.POWERED, false), false);
		scene.idle(5);
		scene.world()
			.setBlock(leverPos, Blocks.AIR.defaultBlockState(), false);
		scene.idle(35);

		scene.world()
			.moveSection(firstOutputTank, util.vector()
				.of(-1, 0, 0), 20);
		scene.idle(25);
		scene.world()
			.showSectionAndMerge(outputHopperS, Direction.DOWN, firstOutputTank);
		scene.idle(10);

		scene.overlay()
			.showText(80)
			.text("Smart Hoppers can also be chained together")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(outputHopper));
		scene.idle(40);

		for (int i = 0; i < 2; i++) {
			drainTank(scene, inputTank, 500);
			setHopperFluid(scene, inputHopperS, 500);
			scene.idle(10);
			clearHopperFluid(scene, inputHopperS);
			setHopperFluid(scene, outputHopperS, 500);
			scene.idle(10);
			clearHopperFluid(scene, outputHopperS);
			fillTank(scene, outputTank, 500);
			scene.idle(12);
		}
		scene.idle(20);

		scene.world()
			.hideSection(inputHopperS.add(inputTankS), Direction.UP);
		scene.world()
			.hideIndependentSection(firstOutputTank, Direction.WEST);
		scene.idle(20);

		ElementLink<WorldSectionElement> itemRig =
			scene.world()
				.showIndependentSection(itemOutputChestS.add(itemHopperS).add(itemInputChestS).add(funnelS),
					Direction.SOUTH);
		scene.world()
			.moveSection(itemRig, util.vector()
				.of(1, 0, -1), 0);
		scene.idle(15);

		scene.overlay()
			.showText(80)
			.text("Smart Hoppers can transfer items as well as fluids")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.centerOf(itemHopperView));
		scene.idle(80);

		scene.overlay()
			.showControls(util.vector()
				.topOf(itemInputChestView), Pointing.DOWN, 40)
			.rightClick()
			.withItem(copper);
		scene.world()
			.modifyBlockEntity(itemInputChest, ChestBlockEntity.class, be -> be.setItem(0, copper.copy()));
		scene.idle(30);
		scene.world()
			.modifyBlockEntity(itemInputChest, ChestBlockEntity.class, be -> be.setItem(0, ItemStack.EMPTY));
		scene.world()
			.modifyBlockEntity(itemOutputChest, ChestBlockEntity.class, be -> be.setItem(0, copper.copy()));
		scene.idle(30);

		scene.world()
			.flapFunnel(funnel, true);
		ElementLink<net.createmod.ponder.api.element.EntityElement> copperDrop = scene.world()
			.createItemEntity(util.vector()
				.centerOf(funnelView)
				.subtract(0, .45, 0), util.vector()
				.of(-0.1, 0, 0), copper.copyWithCount(1));
		scene.world()
			.modifyBlockEntity(itemOutputChest, ChestBlockEntity.class, be -> be.setItem(0, ItemStack.EMPTY));
		scene.idle(45);
		scene.world()
			.modifyEntity(copperDrop, entity -> entity.discard());

		scene.overlay()
			.showText(80)
			.text("Smart Hoppers can also use filters to restrict which items and fluids are allowed through")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector()
				.of(4.5, 1.82, 3.03));
		scene.idle(35);
		scene.overlay()
			.showControls(util.vector()
				.of(4.5, 1.78, 3.03), Pointing.DOWN, 60)
			.rightClick()
			.withItem(iron);
		scene.idle(10);
		scene.overlay()
			.showFilterSlotInput(util.vector()
				.of(4.5, 1.82, 3.12), 50);
		scene.idle(10);
		scene.world()
			.setFilterData(itemHopperS, SmartHopperBlockEntity.class, iron);
		scene.idle(60);

		scene.overlay()
			.showControls(util.vector()
				.topOf(itemInputChestView), Pointing.DOWN, 35)
			.rightClick()
			.withItem(gold);
		scene.world()
			.modifyBlockEntity(itemInputChest, ChestBlockEntity.class, be -> be.setItem(0, gold.copy()));
		scene.idle(60);
		scene.world()
			.modifyBlockEntity(itemInputChest, ChestBlockEntity.class, be -> be.setItem(0, ItemStack.EMPTY));
		scene.idle(25);

		scene.overlay()
			.showControls(util.vector()
				.topOf(itemInputChestView), Pointing.DOWN, 35)
			.rightClick()
			.withItem(iron);
		scene.world()
			.modifyBlockEntity(itemInputChest, ChestBlockEntity.class, be -> be.setItem(0, iron.copy()));
		scene.idle(25);
		scene.world()
			.modifyBlockEntity(itemInputChest, ChestBlockEntity.class, be -> be.setItem(0, ItemStack.EMPTY));
		scene.world()
			.modifyBlockEntity(itemOutputChest, ChestBlockEntity.class, be -> be.setItem(0, iron.copy()));
		scene.idle(20);
		scene.world()
			.flapFunnel(funnel, true);
		scene.world()
			.createItemEntity(util.vector()
				.centerOf(funnelView)
				.subtract(0, .45, 0), util.vector()
				.of(-0.1, 0, 0), iron.copyWithCount(1));
		scene.world()
			.modifyBlockEntity(itemOutputChest, ChestBlockEntity.class, be -> be.setItem(0, ItemStack.EMPTY));
		scene.idle(45);

		scene.markAsFinished();
	}

	private static void fillTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
		scene.world()
			.modifyBlockEntity(pos, FluidTankBlockEntity.class,
				be -> be.getTankInventory()
					.fill(new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE));
	}

	private static void drainTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
		scene.world()
			.modifyBlockEntity(pos, FluidTankBlockEntity.class,
				be -> be.getTankInventory()
					.drain(new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE));
	}

	private static void setHopperFluid(CreateSceneBuilder scene, Selection selection, int amount) {
		scene.world()
			.modifyBlockEntityNBT(selection, SmartHopperBlockEntity.class, nbt -> {
				CompoundTag tank = getPrimaryTank(nbt);
				CompoundTag fluid = new CompoundTag();
				fluid.putString("id", "minecraft:water");
				fluid.putInt("amount", amount);
				tank.put("TankContent", fluid);
				setTankLevel(tank, amount / 1000f);
			});
	}

	private static void clearHopperFluid(CreateSceneBuilder scene, Selection selection) {
		scene.world()
			.modifyBlockEntityNBT(selection, SmartHopperBlockEntity.class, nbt -> {
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

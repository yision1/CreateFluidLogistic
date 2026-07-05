package com.yision.fluidlogistics.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetBlock;
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetBlockEntity;
import net.createmod.catnip.math.Pointing;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class SmartFaucetScenes {

	public static final String SMART_FAUCET = "smart_faucet";

	public static void smartFaucet(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title(SMART_FAUCET, "Filling Items with Smart Faucets");
		scene.configureBasePlate(0, 0, 7);
		scene.showBasePlate();
		scene.idle(5);

		BlockPos faucet1Pos = util.grid().at(0, 2, 5);
		BlockPos tank1Bottom = util.grid().at(0, 1, 6);
		BlockPos tank1Top = util.grid().at(0, 2, 6);
		BlockPos depotPos = util.grid().at(0, 1, 5);
		BlockPos faucet2Pos = util.grid().at(2, 2, 2);
		BlockPos tank2Bottom = util.grid().at(2, 1, 3);
		BlockPos tank2Top = util.grid().at(2, 2, 3);
		BlockPos chestPos = util.grid().at(5, 2, 3);
		BlockPos funnelPos = util.grid().at(5, 2, 2);
		BlockPos depot1V = util.grid().at(3, 1, 3);
		BlockPos basinPos = depot1V;

		Selection tank1Faucet1S = util.select().fromTo(tank1Bottom, tank1Top)
			.add(util.select().position(faucet1Pos));
		Selection depotS = util.select().position(depotPos);
		Selection faucet1S = util.select().position(faucet1Pos);
		Selection tank2S = util.select().fromTo(tank2Bottom, tank2Top);
		Selection faucet2S = util.select().position(faucet2Pos);
		Selection chestS = util.select().position(chestPos);
		Selection funnelS = util.select().position(funnelPos);
		Selection basinS = util.select().position(basinPos);
		Selection largeCog = util.select().position(7, 0, 3);
		Selection kinetics = util.select().position(6, 1, 3);
		Selection belt = util.select().fromTo(0, 1, 2, 6, 1, 2);

		ElementLink<WorldSectionElement> tank1Link =
			scene.world().showIndependentSection(tank1Faucet1S, Direction.DOWN);
		fillMultiTank(scene, tank1Bottom, new FluidStack(Fluids.WATER, 8000));
		fillMultiTank(scene, tank1Bottom, new FluidStack(Fluids.LAVA, 8000));
		scene.world().moveSection(tank1Link, util.vector().of(3, 0, -2), 0);
		scene.idle(5);

		ElementLink<WorldSectionElement> depotLink =
			scene.world().showIndependentSection(depotS, Direction.DOWN);
		scene.world().moveSection(depotLink, util.vector().of(3, 0, -2), 0);
		scene.idle(15);

		scene.overlay()
			.showText(100)
			.text("Smart Faucets can fill items with fluids")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().of(3.5, 2.5, 3.5));
		scene.idle(70);

		ItemStack bucket = new ItemStack(Items.BUCKET);
		scene.world().createItemOnBeltLike(depotPos, Direction.NORTH, bucket);
		scene.idle(20);

		scene.world().modifyBlock(faucet1Pos,
			s -> s.setValue(AbstractFaucetBlock.OPEN, true), false);

		CompoundTag waterTag = fluidTag(new FluidStack(Fluids.WATER, 1000));
		showFaucetFluid(scene, faucet1S, waterTag);
		scene.idle(25);

		hideFaucetFluid(scene, faucet1S);
		scene.world().removeItemsFromBelt(depotPos);
		scene.world().createItemOnBeltLike(depotPos, Direction.UP, new ItemStack(Items.WATER_BUCKET));
		scene.idle(10);

		scene.overlay()
			.showText(80)
			.text("Filters can restrict which fluid types are allowed through")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().of(3.5, 2.78, 3.625));
		scene.idle(40);

		scene.overlay()
			.showFilterSlotInput(util.vector().of(3.5, 2.82, 3.625), Direction.UP, 60);
		scene.idle(10);

		scene.overlay()
			.showControls(util.vector().of(3.5, 2.78, 3.625), Pointing.DOWN, 60)
			.withItem(new ItemStack(Items.LAVA_BUCKET));
		scene.idle(50);

		scene.world().setFilterData(faucet1S, SmartFaucetBlockEntity.class, new ItemStack(Items.LAVA_BUCKET));
		scene.idle(20);

		scene.world().removeItemsFromBelt(depotPos);
		scene.world().createItemOnBeltLike(depotPos, Direction.NORTH, new ItemStack(Items.BUCKET));
		scene.idle(20);

		CompoundTag lavaTag = fluidTag(new FluidStack(Fluids.LAVA, 1000));
		showFaucetFluid(scene, faucet1S, lavaTag);
		scene.idle(25);

		hideFaucetFluid(scene, faucet1S);
		scene.world().removeItemsFromBelt(depotPos);
		scene.world().createItemOnBeltLike(depotPos, Direction.UP, new ItemStack(Items.LAVA_BUCKET));
		scene.idle(30);

		scene.world().hideIndependentSection(depotLink, Direction.NORTH);
		scene.world().setFilterData(faucet1S, SmartFaucetBlockEntity.class, ItemStack.EMPTY);
		scene.idle(20);

		scene.world().setBlock(basinPos, AllBlocks.BASIN.getDefaultState(), false);
		scene.world().showSection(basinS, Direction.SOUTH);
		scene.idle(15);

		scene.overlay()
			.showText(60)
			.text("Smart Faucets can also fill some fluid containers")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(basinPos));
		scene.idle(80);

		CompoundTag basinWaterTag = fluidTag(new FluidStack(Fluids.WATER, 1000));
		showFaucetFluid(scene, faucet1S, basinWaterTag);
		scene.idle(25);

		hideFaucetFluid(scene, faucet1S);
		addBasinFluid(scene, basinPos, new FluidStack(Fluids.WATER, 1000));
		scene.idle(10);

		showFaucetFluid(scene, faucet1S, lavaTag);
		scene.idle(25);

		hideFaucetFluid(scene, faucet1S);
		addBasinFluid(scene, basinPos, new FluidStack(Fluids.LAVA, 1000));

		scene.idle(50);

		scene.world().hideSection(basinS, Direction.UP);
		scene.world().hideIndependentSection(tank1Link, Direction.UP);
		scene.idle(5);
		scene.world().setBlock(basinPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
		scene.idle(30);



		// ========== Phase 3: Belt filling ==========

		Selection scaffoldingS = util.select().position(5, 1, 3);

		scene.world().showSection(largeCog, Direction.UP);
		scene.world().showSection(kinetics, Direction.DOWN);
		scene.world().showSection(scaffoldingS, Direction.DOWN);
		scene.world().showSection(belt, Direction.SOUTH);
		scene.idle(5);
		scene.world().showSection(funnelS, Direction.DOWN);
		scene.world().showSection(chestS, Direction.DOWN);
		scene.idle(10);

		scene.world().modifyBlock(faucet2Pos,
			s -> s.setValue(AbstractFaucetBlock.OPEN, true), false);

		ElementLink<WorldSectionElement> faucet2Link =
			scene.world().showIndependentSection(tank2S, Direction.DOWN);
		fillMultiTank(scene, tank2Bottom, new FluidStack(Fluids.WATER, 8000));
		fillMultiTank(scene, tank2Bottom, new FluidStack(Fluids.LAVA, 8000));
		scene.idle(5);
		scene.world().showSectionAndMerge(faucet2S, Direction.SOUTH, faucet2Link);
		scene.idle(15);

		scene.overlay()
			.showText(80)
			.text("Smart Faucets can also fill items on belts")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().of(2.5, 2.5, 2.5));
		scene.idle(90);

		ItemStack bucket1 = new ItemStack(Items.BUCKET);
		ElementLink<BeltItemElement> bucketItem =
			scene.world().createItemOnBelt(util.grid().at(5, 1, 2), Direction.SOUTH, bucket1);
		scene.idle(45);

		scene.world().stallBeltItem(bucketItem, true);
		scene.idle(10);

		CompoundTag waterTag2 = fluidTag(new FluidStack(Fluids.WATER, 1000));
		showFaucetFluid(scene, faucet2S, waterTag2);
		scene.idle(25);

		hideFaucetFluid(scene, faucet2S);
		scene.world().removeItemsFromBelt(faucet2Pos.below());
		bucketItem = scene.world().createItemOnBelt(faucet2Pos.below(), Direction.UP, new ItemStack(Items.WATER_BUCKET));
		scene.world().stallBeltItem(bucketItem, true);
		scene.world().stallBeltItem(bucketItem, false);
		scene.idle(40);

		scene.overlay()
			.showText(70)
			.text("Without a filter, Smart Faucets choose the fluid intelligently based on the item")
			.placeNearTarget()
			.pointAt(util.vector().of(2.5, 2.5, 2.5));
		scene.idle(80);

		ItemStack powderedObsidian = AllItems.POWDERED_OBSIDIAN.asStack();
		ElementLink<BeltItemElement> obsidianItem =
			scene.world().createItemOnBelt(util.grid().at(5, 1, 2), Direction.SOUTH, powderedObsidian);
		scene.idle(45);

		scene.world().stallBeltItem(obsidianItem, true);
		scene.idle(10);

		CompoundTag lavaTag2 = fluidTag(new FluidStack(Fluids.LAVA, 1000));
		showFaucetFluid(scene, faucet2S, lavaTag2);
		scene.idle(25);

		hideFaucetFluid(scene, faucet2S);
		scene.world().removeItemsFromBelt(faucet2Pos.below());
		obsidianItem = scene.world().createItemOnBelt(faucet2Pos.below(), Direction.UP, AllItems.INCOMPLETE_REINFORCED_SHEET.asStack());
		scene.world().stallBeltItem(obsidianItem, true);
		scene.world().stallBeltItem(obsidianItem, false);

		scene.markAsFinished();
	}

	private static CompoundTag fluidTag(FluidStack fluid) {
		return fluid.writeToNBT(new CompoundTag());
	}

	private static void showFaucetFluid(CreateSceneBuilder scene, Selection faucet, CompoundTag fluid) {
		scene.world().modifyBlockEntityNBT(faucet, SmartFaucetBlockEntity.class,
			nbt -> {
				nbt.put("RenderingFluid", fluid.copy());
				nbt.putBoolean("IsFillingItem", false);
				nbt.putInt("ProcessingTicks", 0);
				nbt.putInt("ProcessingTarget", 0);
				nbt.putInt("TransferCooldown", 40);
			});
	}

	private static void hideFaucetFluid(CreateSceneBuilder scene, Selection faucet) {
		scene.world().modifyBlockEntityNBT(faucet, SmartFaucetBlockEntity.class,
			nbt -> {
				nbt.remove("RenderingFluid");
				nbt.putInt("TransferCooldown", 0);
			});
	}

	private static void addBasinFluid(CreateSceneBuilder scene, BlockPos basinPos, FluidStack fluid) {
		scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
			be.getCapability(ForgeCapabilities.FLUID_HANDLER)
				.ifPresent(handler -> handler.fill(fluid.copy(), FluidAction.EXECUTE));
		});
	}

	private static void fillMultiTank(CreateSceneBuilder scene, BlockPos pos, FluidStack fluid) {
		scene.world().modifyBlockEntity(pos, MultiFluidTankBlockEntity.class,
			be -> be.getTankInventory().fill(fluid, FluidAction.EXECUTE));
	}
}

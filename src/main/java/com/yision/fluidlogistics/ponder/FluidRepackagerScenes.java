package com.yision.fluidlogistics.ponder;

import java.util.List;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageContentHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.content.processing.copperBasin.CopperBasinCapacity;
import com.yision.fluidlogistics.registry.AllBlocks;

import net.createmod.catnip.data.IntAttached;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

public final class FluidRepackagerScenes {

	public static final String FLUID_REPACKAGER = "fluid_repackager";

	private FluidRepackagerScenes() {
	}

	public static void fluidRepackager(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title(FLUID_REPACKAGER, "Merging and Unpacking Packages");
		scene.configureBasePlate(1, 0, 7);
		scene.setSceneOffsetY(-.5f);
		scene.scaleSceneView(.9f);

		BlockPos depotPos = util.grid().at(2, 1, 5);
		BlockPos basinPos = util.grid().at(3, 2, 5);
		BlockPos unpackagerPos = util.grid().at(4, 2, 5);
		BlockPos unpackagerFunnelPos = util.grid().at(5, 2, 5);
		BlockPos mixerPos = util.grid().at(3, 4, 5);
		BlockPos barrelPos = util.grid().at(5, 2, 1);
		BlockPos repackagerPos = util.grid().at(5, 2, 2);
		BlockPos firstInputFunnelPos = util.grid().at(4, 2, 1);
		BlockPos secondInputFunnelPos = util.grid().at(6, 2, 1);
		BlockPos outputFunnelPos = util.grid().at(5, 2, 3);

		Selection scaffolds = util.select().fromTo(3, 1, 5, 4, 1, 5);
		Selection depot = util.select().position(depotPos);
		Selection basin = util.select().position(basinPos);
		Selection unpackager = util.select().position(unpackagerPos);
		Selection unpackagerFunnel = util.select().position(unpackagerFunnelPos);
		Selection mixer = util.select().position(mixerPos);
		Selection initialMachinery = scaffolds.add(depot)
			.add(basin)
			.add(unpackager)
			.add(unpackagerFunnel)
			.add(mixer);

		Selection firstInputFunnel = util.select().position(firstInputFunnelPos);
		Selection secondInputFunnel = util.select().position(secondInputFunnelPos);
		Selection outputFunnel = util.select().position(outputFunnelPos);
		Selection beltFunnels = firstInputFunnel.add(secondInputFunnel)
			.add(outputFunnel);
		Selection belt1 = util.select().fromTo(1, 1, 2, 4, 1, 1);
		Selection belt2 = util.select().fromTo(7, 1, 2, 6, 1, 1);
		Selection belt3 = util.select().fromTo(5, 1, 1, 5, 1, 6)
			.add(util.select().fromTo(6, 1, 5, 6, 1, 6));
		Selection largeCog1 = util.select().position(0, 0, 2);
		Selection largeCog2 = util.select().position(8, 0, 2);
		Selection largeCog3 = util.select().position(6, 0, 7);
		Selection mixerKinetics = util.select().position(3, 0, 7)
			.add(util.select().fromTo(3, 1, 6, 3, 4, 6));
		Selection barrel = util.select().position(barrelPos);
		Selection repackagerAndLever = util.select().fromTo(5, 2, 2, 5, 3, 2);
		Selection thirdStageInputBelt = util.select().fromTo(1, 1, 1, 4, 1, 1);
		Selection thirdStageOutputBelt = util.select().fromTo(5, 1, 1, 5, 1, 5);
		Selection thirdStageHidden = util.select().everywhere()
			.substract(util.select().fromTo(1, 0, 0, 7, 0, 6))
			.substract(thirdStageInputBelt)
			.substract(thirdStageOutputBelt)
			.substract(util.select().position(5, 1, 6))
			.substract(util.select().position(barrelPos));
		Selection thirdStageUnpackager = util.select().position(repackagerPos);
		Selection thirdStageLever = util.select().position(5, 3, 2);
		Selection thirdStageFunnels = util.select().position(firstInputFunnelPos)
			.add(util.select().position(outputFunnelPos));

		FluidStack water = new FluidStack(Fluids.WATER.getSource(), FluidType.BUCKET_VOLUME);
		FluidStack fullBasinWater =
			new FluidStack(Fluids.WATER.getSource(), CopperBasinCapacity.SLOT_CAPACITY);
		ItemStack saplings = new ItemStack(Items.OAK_SAPLING, 16);

		ItemStack fluidPackage = com.yision.fluidlogistics.registry.AllItems.FLUID_PACKAGE.asStack();
		FluidPackageContentHelper.setCanonicalContents(fluidPackage, water);
		ItemStack itemPackage = PackageItem.containing(List.of(saplings.copy()));

		ItemStackHandler mixedContents = FluidPackageContentHelper.createCanonicalContents(water);
		mixedContents.setStackInSlot(1, saplings.copy());
		ItemStack mixedPackage = PackageItem.containing(mixedContents);
		List<ItemStack> splitPackages = PackageResources.splitPackage(mixedPackage);

		ItemStack leaves = new ItemStack(Items.OAK_LEAVES, 16);
		ItemStackHandler separatingContents = FluidPackageContentHelper.createCanonicalContents(water);
		separatingContents.setStackInSlot(1, leaves);
		ItemStack separatingPackage = PackageItem.containing(separatingContents);
		List<ItemStack> separatedPackages = PackageResources.splitPackage(separatingPackage);
		ItemStack separatedFluidPackage = separatedPackages.get(0);
		ItemStack separatedItemPackage = separatedPackages.get(1);

		scene.world().multiplyKineticSpeed(util.select().everywhere(), 1 / 2f);
		scene.world().setKineticSpeed(mixer, 0);
		scene.showBasePlate();
		scene.idle(10);

		scene.world().showSection(scaffolds.add(depot), Direction.UP);
		scene.idle(3);
		scene.world().showSection(basin.add(mixer), Direction.DOWN);
		scene.idle(5);
		scene.world().showSection(unpackager.add(unpackagerFunnel), Direction.WEST);
		scene.idle(10);

		scene.overlay().showText(120)
			.text("Sometimes, requested item cargo and fluid cargo need to arrive in a single package")
			.attachKeyFrame()
			.placeNearTarget()
			.independent(130);
		scene.idle(90);

		scene.world().showSection(belt3, Direction.WEST);
		scene.world().showSection(largeCog3, Direction.UP);
		scene.idle(5);
		scene.world().showSection(belt2, Direction.WEST);
		scene.world().showSection(largeCog2, Direction.UP);
		scene.idle(5);
		scene.world().showSection(belt1, Direction.EAST);
		scene.world().showSection(largeCog1, Direction.UP);
		scene.world().showSection(mixerKinetics, Direction.EAST);
		scene.world().showSection(barrel, Direction.DOWN);
		scene.world().showSection(repackagerAndLever, Direction.NORTH);
		scene.world().setKineticSpeed(mixer, -32);
		scene.idle(20);
		scene.rotateCameraY(-15);
		scene.idle(15);

		scene.overlay().showText(80)
			.text("In this case, redirect the packages into a storage block")
			.attachKeyFrame()
			.pointAt(util.vector().blockSurface(barrelPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(90);

		scene.overlay().showText(70)
			.text("Place a Re-Packager on the storage block and activate it with a redstone signal")
			.attachKeyFrame()
			.pointAt(util.vector().blockSurface(repackagerPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(50);

		scene.world().toggleRedstonePower(repackagerAndLever);
		scene.effects().indicateRedstone(util.grid().at(5, 3, 2));
		scene.idle(20);

		scene.world().showSection(firstInputFunnel.add(secondInputFunnel), Direction.DOWN);
		scene.idle(5);
		scene.world().showSection(outputFunnel, Direction.DOWN);
		scene.idle(20);

		scene.world().createItemOnBelt(util.grid().at(1, 1, 1), Direction.DOWN, fluidPackage);
		scene.world().createItemOnBelt(util.grid().at(7, 1, 1), Direction.DOWN, itemPackage);
		scene.idle(23);
		scene.world().removeItemsFromBelt(secondInputFunnelPos.below());
		scene.world().flapFunnel(secondInputFunnelPos, false);
		scene.idle(63);
		scene.world().removeItemsFromBelt(firstInputFunnelPos.below());
		scene.world().flapFunnel(firstInputFunnelPos, false);
		scene.idle(20);

		PonderHilo.packagerCreate(scene, repackagerPos, mixedPackage);
		scene.effects().indicateSuccess(repackagerPos);
		scene.idle(20);

		PonderHilo.packagerClear(scene, repackagerPos);
		scene.rotateCameraY(15);
		scene.world().multiplyKineticSpeed(util.select().everywhere(), 2);
		scene.world().flapFunnel(outputFunnelPos, true);
		ElementLink<BeltItemElement> mixedPackageOnBelt =
			scene.world().createItemOnBelt(outputFunnelPos.below(), Direction.NORTH, mixedPackage);
		scene.idle(12);
		scene.world().stallBeltItem(mixedPackageOnBelt, true);
		scene.idle(5);

		scene.overlay().showOutlineWithText(unpackager, 100)
			.text("The Unpackager can now split a package containing both item and fluid cargo into the appropriate container")
			.attachKeyFrame()
			.pointAt(util.vector().centerOf(unpackagerPos))
			.placeNearTarget();
		scene.idle(110);

		scene.world().stallBeltItem(mixedPackageOnBelt, false);
		scene.idle(20);
		scene.world().removeItemsFromBelt(unpackagerFunnelPos.below());
		scene.world().flapFunnel(unpackagerFunnelPos, false);
		PonderHilo.packagerUnpack(scene, unpackagerPos, mixedPackage);
		scene.idle(20);

		unpackPackageContents(scene, basinPos, splitPackages);
		scene.idle(20);

		scene.world().modifyBlockEntity(mixerPos, MechanicalMixerBlockEntity.class,
			mixerBlockEntity -> mixerBlockEntity.startProcessingBasin());
		scene.idle(80);

		ItemStack pulp = new ItemStack(AllItems.PULP.get(), 4);
		scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, basinBlockEntity -> {
			basinBlockEntity.getInputInventory().clearContent();
			IFluidHandler fluidHandler =
				basinBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
			if (fluidHandler != null)
				fluidHandler.drain(FluidType.BUCKET_VOLUME, FluidAction.EXECUTE);
		});
		scene.world().modifyBlockEntityNBT(basin, BasinBlockEntity.class, nbt -> nbt.put("VisualizedItems",
			NBTHelper.writeCompoundList(List.of(IntAttached.with(1, pulp.copy())),
				attached -> attached.getValue().serializeNBT())));
		scene.idle(12);

		scene.world().modifyBlockEntityNBT(basin, BasinBlockEntity.class, nbt -> nbt.remove("VisualizedItems"));
		scene.world().createItemOnBeltLike(depotPos, Direction.EAST, pulp);
		scene.idle(40);

		scene.world().removeItemsFromBelt(depotPos);
		scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, basinBlockEntity -> {
			basinBlockEntity.getInputInventory().clearContent();
			basinBlockEntity.getOutputInventory().clearContent();
		});
		scene.idle(10);

		scene.addKeyframe();
		scene.overlay().showControls(util.vector().topOf(basinPos), Pointing.DOWN, 60)
			.withItem(new ItemStack(Items.WATER_BUCKET));
		scene.idle(10);
		fillBasinFluid(scene, basinPos, fullBasinWater);
		scene.idle(50);

		scene.world().multiplyKineticSpeed(util.select().everywhere(), 1 / 2f);
		scene.world().createItemOnBelt(util.grid().at(1, 1, 1), Direction.DOWN, fluidPackage.copy());
		scene.world().createItemOnBelt(util.grid().at(7, 1, 1), Direction.DOWN, itemPackage.copy());
		scene.idle(23);
		scene.world().removeItemsFromBelt(secondInputFunnelPos.below());
		scene.world().flapFunnel(secondInputFunnelPos, false);
		scene.idle(63);
		scene.world().removeItemsFromBelt(firstInputFunnelPos.below());
		scene.world().flapFunnel(firstInputFunnelPos, false);
		scene.idle(20);

		PonderHilo.packagerCreate(scene, repackagerPos, mixedPackage);
		scene.effects().indicateSuccess(repackagerPos);
		scene.idle(20);

		PonderHilo.packagerClear(scene, repackagerPos);
		scene.world().multiplyKineticSpeed(util.select().everywhere(), 2);
		scene.world().flapFunnel(outputFunnelPos, true);
		scene.world().createItemOnBelt(outputFunnelPos.below(), Direction.NORTH, mixedPackage.copy());
		scene.idle(32);
		scene.world().removeItemsFromBelt(unpackagerFunnelPos.below());
		scene.world().flapFunnel(unpackagerFunnelPos, false);
		PonderHilo.packagerUnpack(scene, unpackagerPos, mixedPackage);
		setStalledPackage(scene, unpackager, fluidPackage);
		unpackPackageContents(scene, basinPos, List.of(itemPackage));
		scene.idle(20);

		scene.overlay().showOutlineWithText(unpackager, 120)
			.text("Packages that cannot be fully unpacked will be cached in the Unpackager until the corresponding container can fully unpack them")
			.attachKeyFrame()
			.pointAt(util.vector().centerOf(unpackagerPos))
			.placeNearTarget();
		scene.idle(130);

		scene.world().modifyBlockEntity(mixerPos, MechanicalMixerBlockEntity.class,
			mixerBlockEntity -> mixerBlockEntity.startProcessingBasin());
		scene.idle(80);

		scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, basinBlockEntity -> {
			basinBlockEntity.getInputInventory().clearContent();
			IFluidHandler fluidHandler =
				basinBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
			if (fluidHandler != null)
				fluidHandler.drain(FluidType.BUCKET_VOLUME, FluidAction.EXECUTE);
		});
		scene.world().modifyBlockEntityNBT(basin, BasinBlockEntity.class, nbt -> nbt.put("VisualizedItems",
			NBTHelper.writeCompoundList(List.of(IntAttached.with(1, pulp.copy())),
				attached -> attached.getValue().serializeNBT())));
		scene.idle(12);

		scene.world().modifyBlockEntityNBT(basin, BasinBlockEntity.class, nbt -> nbt.remove("VisualizedItems"));
		scene.world().createItemOnBeltLike(depotPos, Direction.EAST, pulp.copy());
		fillBasinFluid(scene, basinPos, water);
		setStalledPackage(scene, unpackager, ItemStack.EMPTY);
		PonderHilo.packagerUnpack(scene, unpackagerPos, fluidPackage);
		scene.idle(40);

		scene.world().removeItemsFromBelt(depotPos);
		scene.world().hideSection(thirdStageHidden, Direction.DOWN);
		scene.rotateCameraY(-15);
		scene.idle(20);

		scene.overlay().showText(90)
			.text("Additionally, the Unpackager can separate different types of cargo")
			.attachKeyFrame()
			.placeNearTarget()
			.independent(100);
		scene.idle(100);

		scene.world().restoreBlocks(thirdStageLever);
		scene.world().setBlock(repackagerPos, AllBlocks.FLUID_REPACKAGER.getDefaultState()
			.setValue(PackagerBlock.FACING, Direction.SOUTH), false);
		scene.world().showSection(thirdStageUnpackager, Direction.NORTH);
		scene.idle(15);

		scene.overlay().showText(80)
			.text("Place an Unpackager on the storage block and activate it with a redstone signal")
			.attachKeyFrame()
			.pointAt(util.vector().blockSurface(repackagerPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(40);

		scene.world().showSection(thirdStageLever, Direction.DOWN);
		scene.idle(10);
		scene.world().toggleRedstonePower(repackagerAndLever);
		scene.effects().indicateRedstone(util.grid().at(5, 3, 2));
		scene.idle(30);

		scene.world().showSection(thirdStageFunnels, Direction.DOWN);
		scene.idle(20);

		scene.world().multiplyKineticSpeed(util.select().everywhere(), 1 / 2f);
		ElementLink<BeltItemElement> separatingPackageOnBelt =
			scene.world().createItemOnBelt(util.grid().at(1, 1, 1), Direction.DOWN,
				separatingPackage.copy());
		scene.idle(91);
		scene.world().changeBeltItemTo(separatingPackageOnBelt, ItemStack.EMPTY);
		scene.world().removeItemsFromBelt(firstInputFunnelPos.below());
		scene.world().flapFunnel(firstInputFunnelPos, false);
		scene.world().multiplyKineticSpeed(util.select().everywhere(), 2);
		scene.idle(10);

		scene.idle(20);

		PonderHilo.packagerCreate(scene, repackagerPos, separatedFluidPackage);
		scene.idle(20);
		PonderHilo.packagerClear(scene, repackagerPos);
		scene.world().flapFunnel(outputFunnelPos, true);
		scene.world().createItemOnBelt(outputFunnelPos.below(), Direction.NORTH,
			separatedFluidPackage.copy());

		PonderHilo.packagerCreate(scene, repackagerPos, separatedItemPackage);
		scene.idle(20);
		PonderHilo.packagerClear(scene, repackagerPos);
		scene.world().flapFunnel(outputFunnelPos, true);
		scene.world().createItemOnBelt(outputFunnelPos.below(), Direction.NORTH,
			separatedItemPackage.copy());
		scene.idle(40);
	}

	private static void fillBasinFluid(CreateSceneBuilder scene, BlockPos basinPos, FluidStack fluid) {
		scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, basinBlockEntity -> {
			IFluidHandler fluidHandler =
				basinBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
			if (fluidHandler != null)
				fluidHandler.fill(fluid.copy(), FluidAction.EXECUTE);
		});
	}

	private static void setStalledPackage(CreateSceneBuilder scene, Selection unpackager,
			ItemStack packageStack) {
		scene.world().modifyBlockEntityNBT(unpackager, FluidRepackagerBlockEntity.class, nbt -> {
			ListTag stalledPackages = new ListTag();
			if (!packageStack.isEmpty()) {
				ItemStack storedPackage = packageStack.copy();
				storedPackage.setCount(1);
				stalledPackages.add(storedPackage.save(new CompoundTag()));
			}
			nbt.put("StalledPackages", stalledPackages);
		});
	}

	private static void unpackPackageContents(CreateSceneBuilder scene, BlockPos basinPos,
			List<ItemStack> splitPackages) {
		for (ItemStack splitPackage : splitPackages) {
			if (FluidPackageItem.isFluidPackage(splitPackage)) {
				FluidStack fluid = FluidPackageContentHelper.getSingleContainedFluid(splitPackage);
				fillBasinFluid(scene, basinPos, fluid);
				continue;
			}

			ItemStackHandler contents = PackageItem.getContents(splitPackage);
			for (int slot = 0; slot < contents.getSlots(); slot++) {
				ItemStack cargo = contents.getStackInSlot(slot).copy();
				if (cargo.isEmpty())
					continue;

				scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, basinBlockEntity -> {
					IItemHandler itemHandler =
						basinBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
					if (itemHandler != null)
						ItemHandlerHelper.insertItemStacked(itemHandler, cargo.copy(), false);
				});
				scene.world().createItemOnBeltLike(basinPos, Direction.UP, cargo);
			}
		}
	}
}

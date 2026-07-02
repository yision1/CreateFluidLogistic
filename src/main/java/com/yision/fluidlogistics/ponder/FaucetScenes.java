package com.yision.fluidlogistics.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetBlock;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class FaucetScenes {

    public static final String FAUCET = "faucet";

    public static void faucet(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(FAUCET, "Filling Items with Faucets");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos faucetPos = util.grid().at(0, 2, 5);
        BlockPos tankBottom = util.grid().at(0, 1, 6);
        BlockPos tankTop = util.grid().at(0, 2, 6);
        BlockPos depotPos = util.grid().at(0, 1, 5);
        BlockPos basinPos = util.grid().at(3, 1, 3);

        Selection tankFaucetS = util.select().fromTo(tankBottom, tankTop)
            .add(util.select().position(faucetPos));
        Selection faucetS = util.select().position(faucetPos);
        Selection depotS = util.select().position(depotPos);
        Selection basinS = util.select().position(basinPos);
        CompoundTag waterTag = fluidTag("minecraft:water", 1000);
        CompoundTag lavaTag = fluidTag("minecraft:lava", 1000);
        CompoundTag tankWaterTag = fluidTag("minecraft:water", 16000);
        CompoundTag tankLavaTag = fluidTag("minecraft:lava", 16000);

        setMultiTankFluids(scene, util.select().position(tankBottom), tankWaterTag, tankLavaTag);

        ElementLink<WorldSectionElement> tankLink =
            scene.world().showIndependentSection(tankFaucetS, Direction.DOWN);
        scene.world().moveSection(tankLink, util.vector().of(3, 0, -2), 0);
        scene.idle(10);

        ElementLink<WorldSectionElement> depotLink =
            scene.world().showIndependentSection(depotS, Direction.DOWN);
        scene.world().moveSection(depotLink, util.vector().of(3, 0, -2), 0);
        scene.idle(15);

        scene.overlay()
            .showText(100)
            .text("Faucets can fill items with fluids")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().of(3.5, 2.5, 3.5));
        scene.idle(70);

        scene.world().createItemOnBeltLike(depotPos, Direction.NORTH, new ItemStack(Items.BUCKET));
        scene.idle(20);

        scene.world().modifyBlock(faucetPos, s -> s.setValue(FaucetBlock.OPEN, true), false);

        showFaucetFluid(scene, faucetS, waterTag);
        scene.idle(25);

        hideFaucetFluid(scene, faucetS);
        scene.world().removeItemsFromBelt(depotPos);
        scene.world().createItemOnBeltLike(depotPos, Direction.UP, new ItemStack(Items.WATER_BUCKET));
        scene.idle(30);

        scene.overlay()
            .showText(85)
            .text("Faucets choose the fluid intelligently based on the item")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().of(3.5, 2.5, 3.5));
        scene.idle(95);

        scene.world().removeItemsFromBelt(depotPos);
        scene.idle(10);
        scene.world().createItemOnBeltLike(depotPos, Direction.NORTH, AllItems.POWDERED_OBSIDIAN.asStack());
        scene.idle(20);

        showFaucetFluid(scene, faucetS, lavaTag);
        scene.idle(25);

        hideFaucetFluid(scene, faucetS);
        scene.world().removeItemsFromBelt(depotPos);
        scene.world().createItemOnBeltLike(depotPos, Direction.UP, AllItems.INCOMPLETE_REINFORCED_SHEET.asStack());
        scene.idle(30);

        scene.overlay()
            .showText(80)
            .text("Unlike Smart Faucets, Faucets cannot fill items on belts")
            .placeNearTarget()
            .pointAt(util.vector().of(3.5, 2.5, 3.5));
        scene.idle(90);

        scene.world().hideIndependentSection(depotLink, Direction.NORTH);
        scene.idle(20);

        scene.world().setBlock(basinPos, com.simibubi.create.AllBlocks.BASIN.getDefaultState(), false);
        scene.world().showSection(basinS, Direction.SOUTH);
        scene.idle(15);

        scene.overlay()
            .showText(70)
            .text("Faucets can also fill some fluid containers")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(basinPos));
        scene.idle(80);

        showFaucetFluid(scene, faucetS, waterTag);
        scene.idle(25);

        hideFaucetFluid(scene, faucetS);
        addBasinFluid(scene, basinPos, new FluidStack(Fluids.WATER, 1000));
        scene.idle(10);

        showFaucetFluid(scene, faucetS, lavaTag);
        scene.idle(25);

        hideFaucetFluid(scene, faucetS);
        addBasinFluid(scene, basinPos, new FluidStack(Fluids.LAVA, 1000));
        scene.idle(45);

        scene.markAsFinished();
    }

    private static CompoundTag fluidTag(String fluidId, int amount) {
        CompoundTag fluid = new CompoundTag();
        fluid.putString("id", fluidId);
        fluid.putInt("amount", amount);
        return fluid;
    }

    private static void showFaucetFluid(CreateSceneBuilder scene, Selection faucet, CompoundTag fluid) {
        scene.world().modifyBlockEntityNBT(faucet, FaucetBlockEntity.class,
            nbt -> {
                nbt.put("RenderingFluid", fluid.copy());
                nbt.putBoolean("IsFillingItem", false);
                nbt.putInt("ProcessingTicks", 0);
                nbt.putInt("ProcessingTarget", 0);
                nbt.putInt("TransferCooldown", 40);
            });
    }

    private static void hideFaucetFluid(CreateSceneBuilder scene, Selection faucet) {
        scene.world().modifyBlockEntityNBT(faucet, FaucetBlockEntity.class,
            nbt -> {
                nbt.remove("RenderingFluid");
                nbt.putInt("TransferCooldown", 0);
            });
    }

    private static void setMultiTankFluids(CreateSceneBuilder scene, Selection tank, CompoundTag... fluids) {
        scene.world().modifyBlockEntityNBT(tank, MultiFluidTankBlockEntity.class,
            nbt -> {
                CompoundTag tankContent = new CompoundTag();
                for (int i = 0; i < fluids.length; i++) {
                    tankContent.put(Integer.toString(i), fluids[i].copy());
                }
                nbt.put("TankContent", tankContent);
                nbt.putBoolean("ForceFluidLevel", true);
            });
    }

    private static void addBasinFluid(CreateSceneBuilder scene, BlockPos basinPos, FluidStack fluid) {
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            var fh = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), null);
            if (fh == null) {
                return;
            }

            fh.fill(fluid.copy(), FluidAction.EXECUTE);
        });
    }
}

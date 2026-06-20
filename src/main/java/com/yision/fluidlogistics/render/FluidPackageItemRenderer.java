package com.yision.fluidlogistics.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.util.VirtualFluidDisplayHelper;

import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

@OnlyIn(Dist.CLIENT)
public class FluidPackageItemRenderer extends CustomRenderedItemModelRenderer {

    public static final float PACKAGE_VISUAL_WIDTH = 12f / 16f;
    public static final float PACKAGE_VISUAL_HEIGHT = 10f / 16f;

    private static final float PACKAGE_MIN = 2f / 16f;
    private static final float PACKAGE_MAX_XZ = 14f / 16f;
    private static final float PACKAGE_MAX_Y = 10f / 16f;
    private static final float SHRINK = 1f / 128f;

    public static boolean entityRendering = false;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {
        renderer.render(model.getOriginalModel(), light);

        if (stack.getItem() instanceof FluidPackageItem && !entityRendering) {
            renderFluidContents(stack, ms, buffer, light);
        }
    }

    public static void renderFluidContents(ItemStack box, PoseStack ms, MultiBufferSource buffer, int light) {
        renderFluidContents(box, -1, ms, buffer, light);
    }

    public static void renderFluidContents(ItemStack box, float fluidLevel, PoseStack ms, MultiBufferSource buffer, int light) {
        renderFluidContents(box, fluidLevel, ms, buffer, light, CoordinateMode.ITEM_MODEL);
    }

    public static void renderFluidContentsForEntity(ItemStack box, PoseStack ms, MultiBufferSource buffer, int light) {
        renderFluidContentsForEntity(box, -1, ms, buffer, light);
    }

    public static void renderFluidContentsForEntity(ItemStack box, float fluidLevel, PoseStack ms, MultiBufferSource buffer, int light) {
        renderFluidContents(box, fluidLevel, ms, buffer, light, CoordinateMode.CENTERED_ENTITY);
    }

    private static void renderFluidContents(ItemStack box, float fluidLevel, PoseStack ms,
                                            MultiBufferSource buffer, int light, CoordinateMode mode) {
        List<FluidStack> fluids = getContainedFluids(box);
        if (fluids.isEmpty()) return;

        float totalFluid = 0;
        for (FluidStack fluid : fluids) {
            totalFluid += fluid.getAmount();
        }

        if (totalFluid <= 0) return;

        if (fluidLevel < 0) {
            fluidLevel = totalFluid;
        }

        FluidStack primaryFluid = fluids.get(0);
        float fillLevel = Math.min(fluidLevel / Config.getFluidPerPackage(), 1.0f);

        float fluidHeight = PACKAGE_MAX_Y * fillLevel;
        if (fluidHeight <= 0) return;

        float xMin;
        float xMax;
        float zMin;
        float zMax;
        float yMin = SHRINK;
        float yMax = PACKAGE_MAX_Y - SHRINK;

        if (mode == CoordinateMode.CENTERED_ENTITY) {
            xMin = -PACKAGE_VISUAL_WIDTH / 2 + SHRINK;
            xMax = PACKAGE_VISUAL_WIDTH / 2 - SHRINK;
            zMin = -PACKAGE_VISUAL_WIDTH / 2 + SHRINK;
            zMax = PACKAGE_VISUAL_WIDTH / 2 - SHRINK;
        } else {
            xMin = PACKAGE_MIN + SHRINK;
            xMax = PACKAGE_MAX_XZ - SHRINK;
            zMin = PACKAGE_MIN + SHRINK;
            zMax = PACKAGE_MAX_XZ - SHRINK;
        }

        boolean isLighterThanAir = primaryFluid.getFluid().getFluidType().isLighterThanAir();
        if (isLighterThanAir) {
            float yOffset = PACKAGE_MAX_Y - fluidHeight;
            yMin += yOffset;
        }

        float calculatedYMax = yMin + fluidHeight - SHRINK * 2;
        yMax = Math.min(yMax, Math.max(yMin + SHRINK, calculatedYMax));

        if (yMax <= yMin) return;

        boolean needsModelOffset = mode == CoordinateMode.ITEM_MODEL;
        if (needsModelOffset) {
            ms.pushPose();
            ms.translate(-0.5f, -0.5f, -0.5f);
        }

        ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
            primaryFluid,
            xMin, yMin, zMin,
            xMax, yMax, zMax,
            buffer, ms, light,
            true, false
        );

        if (needsModelOffset) {
            ms.popPose();
        }
    }

    public static FluidStack getVisualContainedFluid(ItemStack box) {
        List<FluidStack> fluids = getContainedFluids(box);
        if (fluids.isEmpty()) {
            return FluidStack.EMPTY;
        }

        int totalFluid = 0;
        for (FluidStack fluid : fluids) {
            totalFluid += fluid.getAmount();
        }

        FluidStack displayedFluid = fluids.get(0).copy();
        displayedFluid.setAmount(totalFluid);
        return displayedFluid;
    }

    public static List<FluidStack> getContainedFluids(ItemStack box) {
        List<FluidStack> fluids = new ArrayList<>();

        if (!PackageItem.isPackage(box)) return fluids;

        ItemStackHandler contents = PackageItem.getContents(box);

        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            FluidStack fluid = VirtualFluidDisplayHelper.getPackageDisplayFluid(slotStack);
            if (!fluid.isEmpty()) {
                mergeFluid(fluids, fluid);
            }
        }

        return fluids;
    }

    private static void mergeFluid(List<FluidStack> fluids, FluidStack newFluid) {
        for (FluidStack existing : fluids) {
            if (existing.isFluidEqual(newFluid)) {
                existing.grow(newFluid.getAmount());
                return;
            }
        }
        fluids.add(newFluid.copy());
    }

    private enum CoordinateMode {
        ITEM_MODEL,
        CENTERED_ENTITY
    }
}

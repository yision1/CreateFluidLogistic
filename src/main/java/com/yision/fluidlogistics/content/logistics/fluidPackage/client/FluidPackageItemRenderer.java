package com.yision.fluidlogistics.content.logistics.fluidPackage.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.util.VirtualFluidDisplayHelper;

import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

@OnlyIn(Dist.CLIENT)
public class FluidPackageItemRenderer extends CustomRenderedItemModelRenderer {

    private static final float FLUID_INSET = 1f / 128f;

    public static final float FLUID_MIN_XZ = 3f / 16f + FLUID_INSET;
    public static final float FLUID_MAX_XZ = 13f / 16f - FLUID_INSET;
    public static final float FLUID_MIN_Y = 1f / 16f + FLUID_INSET;
    public static final float FLUID_MAX_Y = 11f / 16f - FLUID_INSET;

    public static final float FLUID_WIDTH = FLUID_MAX_XZ - FLUID_MIN_XZ;
    public static final float FLUID_HEIGHT = FLUID_MAX_Y - FLUID_MIN_Y;

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

        float fillFactor = Mth.clamp(fluidLevel / Config.getFluidPerPackage(), 0f, 1f);
        float renderedHeight = FLUID_HEIGHT * fillFactor;
        if (renderedHeight <= 0) return;

        float yMin;
        float yMax;
        if (primaryFluid.getFluid().getFluidType().isLighterThanAir()) {
            yMax = FLUID_MAX_Y;
            yMin = yMax - renderedHeight;
        } else {
            yMin = FLUID_MIN_Y;
            yMax = yMin + renderedHeight;
        }

        if (yMax <= yMin) return;

        float xMin;
        float xMax;
        float zMin;
        float zMax;
        if (mode == CoordinateMode.ITEM_MODEL) {
            xMin = FLUID_MIN_XZ;
            xMax = FLUID_MAX_XZ;
            zMin = FLUID_MIN_XZ;
            zMax = FLUID_MAX_XZ;
        } else {
            xMin = FLUID_MIN_XZ - 0.5f;
            xMax = FLUID_MAX_XZ - 0.5f;
            zMin = FLUID_MIN_XZ - 0.5f;
            zMax = FLUID_MAX_XZ - 0.5f;
        }

        if (mode == CoordinateMode.ITEM_MODEL) {
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

        if (mode == CoordinateMode.ITEM_MODEL) {
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

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

import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

@OnlyIn(Dist.CLIENT)
public class FluidPackageItemRenderer extends CustomRenderedItemModelRenderer {

    private static final float PACKAGE_MIN = 2f / 16f;
    private static final float PACKAGE_MAX_XZ = 14f / 16f;
    private static final float PACKAGE_MAX_Y = 10f / 16f;
    private static final float PACKAGE_SIZE_XZ = PACKAGE_MAX_XZ - PACKAGE_MIN;
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

        float xMin = PACKAGE_MIN + SHRINK;
        float xMax = PACKAGE_MAX_XZ - SHRINK;
        float yMin = SHRINK;
        float yMax = PACKAGE_MAX_Y - SHRINK;
        float zMin = PACKAGE_MIN + SHRINK;
        float zMax = PACKAGE_MAX_XZ - SHRINK;

        boolean isLighterThanAir = primaryFluid.getFluid().getFluidType().isLighterThanAir();
        if (isLighterThanAir) {
            float yOffset = PACKAGE_MAX_Y - fluidHeight;
            yMin += yOffset;
        }

        float calculatedYMax = yMin + fluidHeight - SHRINK * 2;
        yMax = Math.min(yMax, Math.max(yMin + SHRINK, calculatedYMax));

        if (yMax <= yMin) return;

        ms.pushPose();
        ms.translate(-0.5f, -0.5f, -0.5f);

        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
            primaryFluid,
            xMin, yMin, zMin,
            xMax, yMax, zMax,
            buffer, ms, light,
            true, false
        );

        ms.popPose();
    }

    public static void renderFluidContentsForEntity(ItemStack box, PoseStack ms, MultiBufferSource buffer, int light) {
        renderFluidContentsForEntity(box, -1, ms, buffer, light);
    }

    public static void renderFluidContentsForEntity(ItemStack box, float fluidLevel, PoseStack ms, MultiBufferSource buffer, int light) {
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

        float xMin = -PACKAGE_SIZE_XZ / 2 + SHRINK;
        float xMax = PACKAGE_SIZE_XZ / 2 - SHRINK;
        float yMin = SHRINK;
        float yMax = PACKAGE_MAX_Y - SHRINK;
        float zMin = -PACKAGE_SIZE_XZ / 2 + SHRINK;
        float zMax = PACKAGE_SIZE_XZ / 2 - SHRINK;

        boolean isLighterThanAir = primaryFluid.getFluid().getFluidType().isLighterThanAir();
        if (isLighterThanAir) {
            float yOffset = PACKAGE_MAX_Y - fluidHeight;
            yMin += yOffset;
        }

        float calculatedYMax = yMin + fluidHeight - SHRINK * 2;
        yMax = Math.min(yMax, Math.max(yMin + SHRINK, calculatedYMax));

        if (yMax <= yMin) return;

        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
            primaryFluid,
            xMin, yMin, zMin,
            xMax, yMax, zMax,
            buffer, ms, light,
            true, false
        );
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
            if (FluidStack.isSameFluidSameComponents(existing, newFluid)) {
                existing.grow(newFluid.getAmount());
                return;
            }
        }
        fluids.add(newFluid.copy());
    }
}

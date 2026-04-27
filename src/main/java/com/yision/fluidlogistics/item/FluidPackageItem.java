package com.yision.fluidlogistics.item;

import java.util.function.Consumer;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.render.FluidPackageItemRenderer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class FluidPackageItem extends PackageItem {

    public static final PackageStyle FLUID_STYLE = new PackageStyle("rare_fluid", 12, 10, 21f, true);
    public static final PackageStyle FLUID_STYLE_2 = new PackageStyle("rare_fluid_package_1", 12, 10, 21f, true);

    public FluidPackageItem(Properties properties) {
        this(properties, FLUID_STYLE);
    }

    public FluidPackageItem(Properties properties, PackageStyle style) {
        super(properties, style);
        PackageStyles.ALL_BOXES.remove(this);
        PackageStyles.RARE_BOXES.remove(this);
    }

    public static boolean isFluidPackage(ItemStack stack) {
        return stack.getItem() instanceof FluidPackageItem;
    }

    @Override
    public String getDescriptionId() {
        return "item." + FluidLogistics.MODID + ".rare_fluid_package";
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new FluidPackageItemRenderer()));
    }
}

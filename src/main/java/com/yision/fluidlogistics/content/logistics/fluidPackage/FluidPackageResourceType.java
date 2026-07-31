package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.api.packager.PackageDestroyContext;
import com.yision.fluidlogistics.api.packager.PackageInspection;
import com.yision.fluidlogistics.api.packager.PackageResource;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.api.packager.PackageResourceGoggleInformation;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageResourceType;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.api.packager.PackageUnpackContext;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import com.yision.fluidlogistics.client.FluidTooltipHelper;
import com.yision.fluidlogistics.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.ItemStackHandler;

@ApiStatus.Internal
public final class FluidPackageResourceType {
    private static final PackageResourceType FLUID_TYPE = new FluidResourceType();
    private static boolean builtInsRegistered;

    private FluidPackageResourceType() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static PackageResourceType fluid() {
        return FLUID_TYPE;
    }

    public static ItemStack createFluidKey(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack carrier = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(carrier, FluidHelper.copyStackWithAmount(fluid, 1));
        return FLUID_TYPE.normalizeKey(carrier);
    }

    public static FluidStack getFluid(ItemStack carrierOrKey) {
        if (!FLUID_TYPE.isValidCarrier(carrierOrKey)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(carrierOrKey).copy();
    }

    public static int getFluidPerPackage() {
        return CompressedTankItem.getCapacity();
    }

    @ApiStatus.Internal
    public static synchronized void registerBuiltIns() {
        if (builtInsRegistered) {
            return;
        }
        PackageResources.register(FLUID_TYPE);
        builtInsRegistered = true;
    }

    private static final class FluidResourceType
            implements PackageResourceType, PackageResourceGoggleInformation {
        private static final PackageResourceDisplay DISPLAY = new FluidResourceDisplay();

        @Override
        public ResourceLocation id() {
            return PackageResourceTypes.FLUID;
        }

        @Override
        public Supplier<? extends Item> carrierItem() {
            return AllItems.COMPRESSED_STORAGE_TANK;
        }

        @Override
        public boolean isValidCarrier(ItemStack stack) {
            return stack != null && CompressedTankItem.isFluidStack(stack);
        }

        @Override
        public ItemStack normalizeKey(ItemStack stack) {
            if (stack == null || !(stack.getItem() instanceof CompressedTankItem)) {
                throw new IllegalArgumentException("invalid fluid resource carrier");
            }
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            if (fluid.isEmpty()) {
                throw new IllegalArgumentException("invalid fluid resource carrier");
            }
            ItemStack normalized = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(normalized, FluidHelper.copyStackWithAmount(fluid, 1));
            return normalized;
        }

        @Override
        public boolean matches(ItemStack firstNormalizedKey, ItemStack secondNormalizedKey) {
            if (!isValidCarrier(firstNormalizedKey) || !isValidCarrier(secondNormalizedKey)) {
                return false;
            }
            FluidStack first = CompressedTankItem.getFluid(firstNormalizedKey);
            FluidStack second = CompressedTankItem.getFluid(secondNormalizedKey);
            return first.isFluidEqual(second) && FluidStack.areFluidStackTagsEqual(first, second);
        }

        @Override
        public int identityHash(ItemStack normalizedKey) {
            return Objects.hash(normalizedKey.getItem(), normalizedKey.getTag());
        }

        @Override
        public int amountOf(ItemStack carrierStack) {
            if (carrierStack == null || !(carrierStack.getItem() instanceof CompressedTankItem)) {
                throw new IllegalArgumentException("invalid fluid resource carrier");
            }
            FluidStack fluid = CompressedTankItem.getFluid(carrierStack);
            if (fluid.isEmpty()) {
                throw new IllegalArgumentException("invalid fluid resource carrier");
            }
            return Math.multiplyExact(fluid.getAmount(), carrierStack.getCount());
        }

        @Override
        public ItemStack createCarrier(ItemStack normalizedKey, int amount) {
            if (amount <= 0 || amount > CompressedTankItem.getCapacity()) {
                throw new IllegalArgumentException("invalid fluid resource amount");
            }
            ItemStack carrier = normalizeKey(normalizedKey);
            CompressedTankItem.setFluid(carrier,
                    FluidHelper.copyStackWithAmount(CompressedTankItem.getFluid(normalizedKey), amount));
            return carrier;
        }

        @Override
        public int maxPerPackage(ItemStack normalizedKey) {
            normalizeKey(normalizedKey);
            return CompressedTankItem.getCapacity();
        }

        @Override
        public ItemStack createPackage(ItemStack normalizedKey, int amount) {
            ItemStack carrier = createCarrier(normalizedKey, amount);
            return FluidPackageContentHelper.createCanonicalPackage(CompressedTankItem.getFluid(carrier));
        }

        @Override
        public Optional<PackageResource> readCanonicalPackage(ItemStack packageStack) {
            if (!FluidPackageItem.isFluidPackage(packageStack)) {
                return Optional.empty();
            }
            FluidStack fluid = FluidPackageContentHelper.getSingleContainedFluid(packageStack);
            if (fluid.isEmpty()) {
                return Optional.empty();
            }
            ItemStack key = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(key, FluidHelper.copyStackWithAmount(fluid, 1));
            return Optional.of(new PackageResource(PackageResourceTypes.FLUID, key, fluid.getAmount()));
        }

        @Override
        public boolean unpack(PackageUnpackContext context, PackageResource resource, boolean simulate) {
            if (!resource.typeId().equals(PackageResourceTypes.FLUID)) {
                return false;
            }
            Level level = context.level();
            BlockEntity blockEntity = context.targetBlockEntity();
            IFluidHandler handler = blockEntity == null ? null
                    : blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, context.side()).orElse(null);
            if (handler == null) {
                return false;
            }
            FluidStack fluid = FluidHelper.copyStackWithAmount(
                    CompressedTankItem.getFluid(resource.key()), resource.amount());
            int accepted = handler.fill(fluid.copy(), FluidAction.SIMULATE);
            if (accepted != fluid.getAmount()) {
                return false;
            }
            return simulate || handler.fill(fluid.copy(), FluidAction.EXECUTE) == fluid.getAmount();
        }

        @Override
        public PackageResourceDisplay display() {
            return DISPLAY;
        }

        @Override
        public boolean addToGoggleTooltip(
                List<Component> tooltip,
                boolean isPlayerSneaking,
                ItemStack packageStack,
                List<PackageResource> resources) {
            List<FluidStack> fluids = resources.stream()
                    .map(resource -> FluidHelper.copyStackWithAmount(
                            getFluid(resource.key()), resource.amount()))
                    .filter(fluid -> !fluid.isEmpty())
                    .toList();
            return FluidPackageGoggleInfo.append(tooltip, fluids, CompressedTankItem.getCapacity());
        }

        @Override
        public boolean survivesWater(ItemStack packageStack, PackageInspection inspection) {
            return inspection.canonical() && FluidPackageItem.isFluidPackage(packageStack);
        }

        @Override
        public SawAction sawAction(ItemStack packageStack, PackageInspection inspection) {
            return inspection.canonical() && FluidPackageItem.isFluidPackage(packageStack)
                    ? SawAction.DESTROY_WITHOUT_DROPS
                    : SawAction.DEFAULT;
        }

        @Override
        public DropAction onDestroyed(PackageDestroyContext context, List<PackageResource> resources) {
            ServerLevel level = context.level();
            PackageEntity entity = context.entity();
            boolean selectedFluid = false;
            ItemStackHandler contents = PackageItem.getContents(context.packageStack());
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                ItemStack carrier = contents.getStackInSlot(slot);
                if (!isValidCarrier(carrier)) {
                    continue;
                }
                if (!selectedFluid && tryPlaceFluid(level, entity.blockPosition(), carrier)) {
                    selectedFluid = true;
                }
            }
            return DropAction.CONSUME_CARRIERS;
        }

        private static boolean tryPlaceFluid(ServerLevel level, BlockPos pos, ItemStack carrier) {
            FluidStack fluid = CompressedTankItem.getFluid(carrier);
            if (fluid.getAmount() < FluidAmountHelper.MB_PER_BUCKET) {
                return false;
            }
            if (level.dimensionType().ultraWarm() && fluid.getFluid().is(FluidTags.WATER)) {
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                        2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
                for (int i = 0; i < 8; i++) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            pos.getX() + level.random.nextDouble(),
                            pos.getY() + level.random.nextDouble(),
                            pos.getZ() + level.random.nextDouble(),
                            1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                return true;
            }
            BlockState fluidState = fluid.getFluid().defaultFluidState().createLegacyBlock();
            if (fluidState.isAir()) {
                return true;
            }
            BlockState existingState = level.getBlockState(pos);
            if (!existingState.canBeReplaced() || !fluidState.canSurvive(level, pos)) {
                return true;
            }
            level.setBlock(pos, fluidState, Block.UPDATE_ALL);
            return true;
        }
    }

    private static final class FluidResourceDisplay implements PackageResourceDisplay {
        private static final int MAX_FACTORY_PANEL_AMOUNT = 100 * FluidAmountHelper.MB_PER_BUCKET;
        private static final List<FactoryPanelUnit> FACTORY_PANEL_UNITS = List.of(
                new FactoryPanelUnit("mB", 1),
                new FactoryPanelUnit("B", FluidAmountHelper.MB_PER_BUCKET));

        @Override
        public String baseUnit() {
            return "mB";
        }

        @Override
        public String format(ItemStack normalizedKey, int amount, Format format) {
            Objects.requireNonNull(format, "format");
            return switch (format) {
                case COMPACT -> FluidAmountHelper.format(amount);
                case PRECISE -> FluidAmountHelper.formatPrecise(amount);
                case DETAILED -> FluidAmountHelper.formatDetailed(amount);
            };
        }

        @Override
        public Component name(ItemStack normalizedKey) {
            return getFluid(normalizedKey).getDisplayName().copy();
        }

        @Override
        public List<Component> tooltip(ItemStack normalizedKey, boolean advanced) {
            TooltipFlag flag = advanced ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
            return FluidTooltipHelper.getTooltipLines(getFluid(normalizedKey), flag);
        }

        @Override
        public List<Component> tooltip(ItemStack normalizedKey, int amount, boolean advanced) {
            List<Component> lines = new ArrayList<>(tooltip(normalizedKey, advanced));
            if (!lines.isEmpty()) {
                lines.add(1, Component.literal(format(normalizedKey, amount, Format.PRECISE))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return List.copyOf(lines);
        }

        @Override
        public boolean showsAmountInTooltip(TooltipContext context) {
            return context == TooltipContext.STOCK_KEEPER_ORDER
                    || context == TooltipContext.STOCK_KEEPER_CRAFTABLE;
        }

        @Override
        public List<FactoryPanelUnit> factoryPanelUnits(ItemStack normalizedKey) {
            return FACTORY_PANEL_UNITS;
        }

        @Override
        public int factoryPanelMaxValue(ItemStack normalizedKey) {
            return 100;
        }

        @Override
        public int factoryPanelMilestoneInterval(ItemStack normalizedKey) {
            return 10;
        }

        @Override
        public FactoryPanelRestockPolicy factoryPanelRestockPolicy(ItemStack normalizedKey) {
            long configuredBatch = (long) Config.getFluidPerPackage() * 100L;
            int batchLimit = (int) Math.max(1L, Math.min(MAX_FACTORY_PANEL_AMOUNT, configuredBatch));
            return new FactoryPanelRestockPolicy(
                    true, true, true, MAX_FACTORY_PANEL_AMOUNT, batchLimit);
        }

        @Override
        public int adjust(ItemStack normalizedKey, Adjustment adjustment) {
            Objects.requireNonNull(adjustment, "adjustment");
            if (adjustment.interaction() == Interaction.STOCK_KEEPER
                    || adjustment.interaction() == Interaction.STOCK_KEEPER_INVENTORY) {
                return FluidAmountHelper.adjustStockKeeperFluidRequestAmount(
                        adjustment.currentAmount(),
                        adjustment.forward(),
                        adjustment.shift(),
                        adjustment.control(),
                        adjustment.minAmount(),
                        adjustment.maxAmount(),
                        adjustment.steps());
            }
            return FluidAmountHelper.adjustFluidRequestAmount(
                    adjustment.currentAmount(),
                    adjustment.forward(),
                    adjustment.shift(),
                    adjustment.control(),
                    adjustment.minAmount(),
                    adjustment.maxAmount(),
                    adjustment.steps());
        }
    }
}

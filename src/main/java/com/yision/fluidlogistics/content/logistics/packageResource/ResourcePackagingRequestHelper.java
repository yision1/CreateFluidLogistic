package com.yision.fluidlogistics.content.logistics.packageResource;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.world.item.ItemStack;

@ApiStatus.Internal
final class ResourcePackagingRequestHelper {
    private ResourcePackagingRequestHelper() {
    }

    public static PackageFragment consumeFirst(List<PackagingRequest> queuedRequests, int transferred) {
        Objects.requireNonNull(queuedRequests, "queuedRequests");
        if (queuedRequests.isEmpty()) {
            throw new IllegalArgumentException("cannot consume an empty packaging request queue");
        }

        PackagingRequest request = Objects.requireNonNull(queuedRequests.getFirst(), "first packaging request");
        if (transferred <= 0 || transferred > request.getCount()) {
            throw new IllegalArgumentException("transferred amount must be within the first request");
        }

        String address = request.address();
        int orderId = request.orderId();
        int linkIndex = request.linkIndex();
        boolean finalLink = request.finalLink().booleanValue();
        int packageIndex = request.packageCounter().getAndIncrement();
        PackageOrderWithCrafts context = request.context();

        request.subtract(transferred);
        boolean finalPackage = false;
        if (request.isEmpty()) {
            finalPackage = true;
            queuedRequests.removeFirst();
            if (!queuedRequests.isEmpty()) {
                PackagingRequest following = queuedRequests.getFirst();
                if (sameSequence(address, orderId, linkIndex, following)) {
                    following.packageCounter().setValue(packageIndex + 1);
                    finalPackage = false;
                }
            }
        }

        return new PackageFragment(
                address, orderId, linkIndex, finalLink, packageIndex, finalPackage, context);
    }

    private static boolean sameSequence(
            @Nullable String address, int orderId, int linkIndex, PackagingRequest request) {
        return Objects.equals(address, request.address())
                && orderId == request.orderId()
                && linkIndex == request.linkIndex();
    }

    public record PackageFragment(
            @Nullable String address,
            int orderId,
            int linkIndex,
            boolean finalLink,
            int packageIndex,
            boolean finalPackage,
            @Nullable PackageOrderWithCrafts context) {

        public void applyTo(ItemStack packageStack) {
            Objects.requireNonNull(packageStack, "packageStack");
            PackageItem.clearAddress(packageStack);
            if (address != null) {
                PackageItem.addAddress(packageStack, address);
            }
            PackageItem.setOrder(
                    packageStack, orderId, linkIndex, finalLink, packageIndex, finalPackage, context);
        }
    }
}

package com.yision.fluidlogistics.content.logistics.packageResource;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ResourceRestockSettings {
    int fluidlogistics$getRestockThreshold();

    void fluidlogistics$setRestockThreshold(int threshold);

    int fluidlogistics$getPromiseLimit();

    void fluidlogistics$setPromiseLimit(int limit);

    boolean fluidlogistics$hasPromiseLimit();

    int fluidlogistics$getAdditionalStock();

    void fluidlogistics$setAdditionalStock(int amount);

    boolean fluidlogistics$hasAdditionalStock();

    int fluidlogistics$getRemainingAdditionalStock();
}

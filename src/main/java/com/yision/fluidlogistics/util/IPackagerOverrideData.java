package com.yision.fluidlogistics.util;

import com.yision.fluidlogistics.api.handpointer.PackagerAddress;

public interface IPackagerOverrideData extends PackagerAddress {
    boolean fluidlogistics$isManualOverrideLocked();

    void fluidlogistics$setManualOverrideLocked(boolean locked);

    int fluidlogistics$getQueuedPackageCount();
}

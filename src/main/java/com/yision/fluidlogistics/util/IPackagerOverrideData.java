package com.yision.fluidlogistics.util;

public interface IPackagerOverrideData {

    boolean fluidlogistics$isManualOverrideLocked();

    void fluidlogistics$setManualOverrideLocked(boolean locked);

    String fluidlogistics$getClipboardAddress();

    void fluidlogistics$setClipboardAddress(String address);
}

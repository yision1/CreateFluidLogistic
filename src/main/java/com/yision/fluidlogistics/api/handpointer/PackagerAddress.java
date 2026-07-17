package com.yision.fluidlogistics.api.handpointer;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface PackagerAddress {
    String clipboardAddress();

    void setClipboardAddress(String address);
}

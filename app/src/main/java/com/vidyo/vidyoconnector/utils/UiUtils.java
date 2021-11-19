package com.vidyo.vidyoconnector.utils;

import android.os.Looper;

public final class UiUtils {
    public static boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }
}

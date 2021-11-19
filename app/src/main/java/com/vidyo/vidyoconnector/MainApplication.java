
package com.vidyo.vidyoconnector;

import android.app.Application;

import com.vidyo.vidyoconnector.audio.BluetoothManager;
import com.vidyo.vidyoconnector.utils.AppUtils;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        /* Place some application initialization related code here. */

        if (!AppUtils.isEmulator())
            BluetoothManager.initBluetoothManager(getApplicationContext());
    }
}
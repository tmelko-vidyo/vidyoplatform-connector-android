
package com.vidyo.vidyoconnector;

import android.app.Application;

import com.vidyo.vidyoconnector.audio.BluetoothManager;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        /* Place some application initialization related code here. */

        BluetoothManager.initBluetoothManager(getApplicationContext());
    }
}